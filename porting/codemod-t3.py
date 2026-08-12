#!/usr/bin/env python3
"""
codemod-t3.py — T3, the mechanical codemod sweep.

Applies PURE TEXTUAL, token-level, semantics-preserving Forge -> NeoForge renames
across every .java file in src/main, src/test and src/gametest. Every file this
script touches is still listed in porting/unported.txt (or unported-test.txt) and
therefore excluded from compilation by sourceSets.main.java/test.java — nothing
here is expected to compile yet. The point is to retire renaming noise now, so
that the PRs which actually port each package (T4+) show real semantic work in
their diffs instead of Forge->NeoForge search-and-replace mixed in with it.

Four transform categories, run in this order:
  1. PACKAGE_RENAMES   - import-line package/prefix renames, simple class name
                          unchanged (e.g. net.minecraftforge.fluids.FluidStack
                          -> net.neoforged.neoforge.fluids.FluidStack).
  2. SPECIAL_RENAMES    - import + whole-file token rename, for the handful of
                          classes whose simple name also changed (e.g.
                          ToolAction -> ItemAbility, RegistryObject ->
                          DeferredHolder). Only fires in a file that actually
                          imports the old class.
  3. REGISTRY_FIELD_RENAMES - net.minecraftforge.registries.ForgeRegistries.<X>
                          -> net.minecraft.core.registries.BuiltInRegistries.<Y>
                          or net.neoforged.neoforge.registries.NeoForgeRegistries
                          .Keys.<Y>, member-by-member (the field names are not
                          the same string - Forge's are plural, vanilla's are
                          singular - so this cannot be a prefix rule).
  4. RESOURCE_LOCATION_CTOR - new ResourceLocation(a, b) -> ResourceLocation
                          .fromNamespaceAndPath(a, b); new ResourceLocation(s)
                          -> ResourceLocation.parse(s). Balanced-paren argument
                          scan, not a naive regex, so nested calls/generics in
                          the arguments are not corrupted.
  5. DROP_IMPORTS       - a small list of now-meaningless SimpleChannel-era
                          imports whose role is fully absorbed by Mantle's
                          IPacket path; dropped with no replacement import.

Every mapping below is backed by one of:
  (a) a diff of the same file across ../mantle-1.20 (branch `1.20`) and
      ../mantle-1.21 (branch `port/1.21/11-client-models`, the current tip of
      that sibling repo's port stack) - see porting/_t3_evidence.md in the
      note this PR ships outside the repo for the extraction method;
  (b) direct verification against the real 1.21.1/NeoForge 21.1.248 jars this
      build compiles against (neoforge-21.1.248-sources.jar, the fancymodloader
      loader-4.0.43 jar for net.neoforged.fml.*, and bus-8.0.5 for
      net.neoforged.bus.api.*);
  (c) M3/M6/M8/T2's own design notes (../*-design.md, read-only, clean-room
      permitted per the porting plan), where a class's shape was independently
      confirmed unchanged (e.g. IContainerFactory, ForgeFlowingFluid.Properties).

DO-NOT-TOUCH, honored two ways:
  - Whole-file skip: any path under a directory in DO_NOT_TOUCH_DIRS is never
    opened for writing at all (library/tools/nbt/ - the ItemStack/tag storage
    migration T2 already flagged as its own future slice).
  - Guarded symbols: imports/fields that look renameable by a naive prefix
    rule but have NO 1:1 NeoForge target (capabilities, LazyOptional,
    ForgeRegistries.ENCHANTMENTS, ContextKey-adjacent network types, etc.) are
    listed in EXCLUDE_EXACT / EXCLUDE_PREFIX and are never rewritten. Every
    file containing one is counted in the "guarded" report so the judgment
    inventory is visible for later slices.

Idempotent: run twice, second run is a zero-diff no-op, because every
transform's trigger pattern (an old net.minecraftforge.* string) is exactly
what the transform removes.

Usage:
    python3 porting/codemod-t3.py [--dry-run] [--check]

    --dry-run   Print the transform table without writing any files.
    --check     Exit 1 if applying the sweep would change any file (used to
                demonstrate idempotence: run once for real, then `--check`
                must report zero).
"""
from __future__ import annotations

import argparse
import pathlib
import re
import sys
from collections import Counter, defaultdict

REPO_ROOT = pathlib.Path(__file__).resolve().parent.parent
SOURCE_ROOTS = [
    REPO_ROOT / "src" / "main" / "java",
    REPO_ROOT / "src" / "test" / "java",
    REPO_ROOT / "src" / "gametest" / "java",
]

# ---------------------------------------------------------------------------
# DO-NOT-TOUCH: whole files never opened for writing.
# ---------------------------------------------------------------------------
DO_NOT_TOUCH_DIRS = [
    "/library/tools/nbt/",  # T2 #103 (ItemStack#tag): the component-storage
                             # migration is its own future slice ("T-A2" per
                             # notes/T-A1-design.md S7); nothing here is a rename.
]

# ---------------------------------------------------------------------------
# 1. PACKAGE_RENAMES - (old prefix, new prefix), longest-old-prefix first.
#    Only ever rewrites import lines (optionally `import static `). The simple
#    class name is unchanged by every rule in this table - that is what makes
#    it a prefix rule rather than a SPECIAL_RENAMES entry.
# ---------------------------------------------------------------------------
PACKAGE_RENAMES: list[tuple[str, str]] = [
    # --- eventbus: package split clean out of net.minecraftforge entirely ---
    ("net.minecraftforge.eventbus.api.Event.Result", None),  # EXCLUDE, see below
    ("net.minecraftforge.eventbus.api.Cancelable", None),    # EXCLUDE, see below
    ("net.minecraftforge.eventbus.api.", "net.neoforged.bus.api."),

    # --- fml: net.minecraftforge.fml/javafmlmod -> net.neoforged.fml, except
    #     three classes with no NeoForge successor (see EXCLUDE_EXACT) ---
    ("net.minecraftforge.fml.", "net.neoforged.fml."),

    # --- api.distmarker: Dist moved out from under fml into its own root ---
    ("net.minecraftforge.api.distmarker.", "net.neoforged.api.distmarker."),

    # --- neoforgespi (was forgespi): package renamed net.minecraftforge.forgespi
    #     -> net.neoforged.neoforgespi (root-level rename, not nested under fml
    #     or neoforge). Two members renamed away (IModLanguageProvider,
    #     IModProvider) and excluded below; the rest verified present in
    #     fancymodloader loader-4.0.43.jar under the mapped path. ---
    ("net.minecraftforge.forgespi.", "net.neoforged.neoforgespi."),

    # --- common.crafting.conditions -> common.conditions (package flattens,
    #     "crafting" drops) - verified via Mantle diff (10 hits) ---
    ("net.minecraftforge.common.crafting.conditions.", "net.neoforged.neoforge.common.conditions."),

    # --- common: everything else is a straight prefix swap onto
    #     net.neoforged.neoforge.common, member-verified against the sources
    #     jar; genuine casualties (capabilities, LazyOptional, ForgeConfigSpec,
    #     MinecraftForge, ToolAction(s), ForgeHooks, ForgeMod, PlantType,
    #     TierSortingRegistry, IForgeShearable, ForgeBiomeModifiers,
    #     IForgeBlockEntity, CanToolPerformAction, and the crafting-ingredient
    #     interfaces) are excluded below or handled as SPECIAL_RENAMES. ---
    ("net.minecraftforge.common.", "net.neoforged.neoforge.common."),

    # --- client: same story - one prefix rule, three casualties excluded
    #     below (ForgeHooksClient -> ClientHooks, RenderGuiOverlayEvent and
    #     VanillaGuiOverlay -> the 1.21 GUI-layer rework, all real renames
    #     with a different simple name, not swept). ---
    ("net.minecraftforge.client.", "net.neoforged.neoforge.client."),

    ("net.minecraftforge.data.", "net.neoforged.neoforge.data."),
    ("net.minecraftforge.energy.", "net.neoforged.neoforge.energy."),
    ("net.minecraftforge.entity.", "net.neoforged.neoforge.entity."),
    ("net.minecraftforge.event.", "net.neoforged.neoforge.event."),
    ("net.minecraftforge.fluids.", "net.neoforged.neoforge.fluids."),
    ("net.minecraftforge.gametest.", "net.neoforged.neoforge.gametest."),
    ("net.minecraftforge.items.", "net.neoforged.neoforge.items."),
    ("net.minecraftforge.network.", "net.neoforged.neoforge.network."),
    ("net.minecraftforge.registries.", "net.neoforged.neoforge.registries."),
    ("net.minecraftforge.server.", "net.neoforged.neoforge.server."),
]
# Drop the two Event.Result/Cancelable placeholders above - they exist only so
# the table above documents the exclusion inline; the real exclusion mechanism
# is EXCLUDE_EXACT below.
PACKAGE_RENAMES = [(o, n) for o, n in PACKAGE_RENAMES if n is not None]
PACKAGE_RENAMES.sort(key=lambda pair: -len(pair[0]))

# ---------------------------------------------------------------------------
# EXCLUDE_EXACT / EXCLUDE_PREFIX: imports a PACKAGE_RENAMES prefix would
# otherwise touch, but which have NO 1:1 NeoForge target - verified MISSING
# (or verified "different mechanism, not a rename") against the real jars.
# Never rewritten; counted in the guard report instead.
# ---------------------------------------------------------------------------
EXCLUDE_PREFIX = [
    # Capabilities are a rework, not a rename (M3 S8: LazyOptional and
    # ICapabilityProvider#getCapability are both gone outright, replaced by
    # synchronous BlockCapability/ItemCapability/EntityCapability queries).
    # Verified MISSING from the 21.1.248 sources jar: Capability,
    # CapabilityManager, CapabilityToken, ForgeCapabilities,
    # ICapabilityProvider, ICapabilitySerializable. RegisterCapabilitiesEvent
    # *does* exist (moved to net.neoforged.neoforge.capabilities, dropping
    # "common"), but it is inseparable from the same rework, so the whole
    # subtree is guarded rather than half-renamed.
    "net.minecraftforge.common.capabilities.",
]

EXCLUDE_EXACT = {
    # eventbus.api: annotation -> marker-interface mechanism change, not a
    # rename (verified: net.neoforged.bus.api.Event has no nested Result;
    # ICancellableEvent is an interface a class implements, replacing the
    # @Cancelable annotation).
    "net.minecraftforge.eventbus.api.Cancelable",
    "net.minecraftforge.eventbus.api.Event.Result",

    # fml: three classes verified MISSING from fancymodloader loader-4.0.43.jar,
    # or (ModLoadingContext) present but flagged unreliable by M3 S4/S8 - its
    # own prior verification found it absent from the classpath this project
    # actually compiles against, and the 1-arg convenience path it backed has
    # no caller here either. Left guarded rather than re-litigated.
    "net.minecraftforge.fml.DistExecutor",
    "net.minecraftforge.fml.ModLoadingContext",
    "net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext",

    # forgespi: renamed away, not just moved (verified MISSING under
    # neoforgespi; IModLanguageLoader/IModFileCandidateLocator look like the
    # successors but that is a rename decision, not this script's job).
    "net.minecraftforge.forgespi.language.IModLanguageProvider",
    "net.minecraftforge.forgespi.locating.IModProvider",

    # common: ForgeConfigSpec, MinecraftForge, ToolAction(s), ForgeFlowingFluid,
    # ForgeSpawnEggItem, IForgeMenuType are handled as SPECIAL_RENAMES, not
    # excluded - listed there, not here.
    "net.minecraftforge.common.ForgeHooks",             # -> CommonHooks, different simple name, not swept
    "net.minecraftforge.common.ForgeI18n",               # gone outright (M3 S8), no replacement at all
    "net.minecraftforge.common.ForgeMod",                # -> NeoForgeMod, different simple name, not swept
    "net.minecraftforge.common.IForgeShearable",         # -> IShearable, different simple name, not swept
    "net.minecraftforge.common.PlantType",               # verified MISSING, gone outright
    "net.minecraftforge.common.TierSortingRegistry",     # verified MISSING under any mapped path
    "net.minecraftforge.common.crafting.AbstractIngredient",       # gone (M8 S4), ICustomIngredient replaces the concept
    "net.minecraftforge.common.crafting.ConditionalAdvancement",   # gone; ConditionalRecipeOutput is a different shape
    "net.minecraftforge.common.crafting.ConditionalRecipe",        # gone; RecipeOutput#withConditions replaces it (M8 S6.2)
    "net.minecraftforge.common.crafting.IIngredientSerializer",    # gone (M8 S4), ICustomIngredient/IngredientType replace it
    "net.minecraftforge.common.crafting.IShapedRecipe",            # verified MISSING
    "net.minecraftforge.common.crafting.VanillaIngredientSerializer",  # gone with IIngredientSerializer
    "net.minecraftforge.common.crafting.conditions.IConditionSerializer",  # gone (M8 S5): a condition owns its own MapCodec now
    "net.minecraftforge.common.extensions.IForgeBlockEntity",      # -> IBlockEntityExtension, different simple name, not swept
    "net.minecraftforge.common.loot.CanToolPerformAction",         # verified MISSING under any mapped path
    "net.minecraftforge.common.util.LazyOptional",       # gone outright, capability rework (see EXCLUDE_PREFIX above)
    "net.minecraftforge.common.util.NonNullConsumer",    # gone outright (M3 S4)
    "net.minecraftforge.common.world.ForgeBiomeModifiers.AddFeaturesBiomeModifier",  # -> BiomeModifiers, different simple name
    "net.minecraftforge.common.world.ForgeBiomeModifiers.AddSpawnsBiomeModifier",    # -> BiomeModifiers, different simple name

    # client: three renamed-with-different-name or reworked-outright classes.
    "net.minecraftforge.client.ForgeHooksClient",                  # -> ClientHooks, different simple name, not swept
    "net.minecraftforge.client.event.RenderGuiOverlayEvent",       # 1.21 GUI-layer rework -> RenderGuiLayerEvent, real rework
    "net.minecraftforge.client.gui.overlay.VanillaGuiOverlay",     # same rework -> client.gui.VanillaGuiLayers

    # registries: ForgeRegistries(.Keys) handled by REGISTRY_FIELD_RENAMES
    # (member-by-member); RegistryObject is a SPECIAL_RENAMES entry. The rest
    # below have no target at all.
    "net.minecraftforge.registries.ForgeRegistry",       # impl type, no public NeoForge successor found
    "net.minecraftforge.registries.GameData",             # single low-confidence body-adjacent use (raw registry-id packet read); network body is slice work
    "net.minecraftforge.registries.IForgeRegistry",       # gone outright (M3 S4)
    "net.minecraftforge.registries.MissingMappingsEvent", # gone outright, no replacement (M3 S4)

    # network: NetworkEvent.Context is handled by DROP_IMPORTS (Mantle's
    # IPacket path absorbs it); these three have no direct target - the
    # mechanism they backed (SimpleChannel registration/versioning/direction)
    # is itself gone, per M6 SS3-4,7.
    "net.minecraftforge.network.NetworkDirection",        # -> PacketFlow is a real rename, but PLAY_TO_CLIENT/SERVER usage-site
                                                            # rewrite is not import-only (M6 S11); left for the network slice
    "net.minecraftforge.network.NetworkHooks",             # no successor; NeoForge menus open through vanilla APIs directly
    "net.minecraftforge.network.NetworkRegistry",          # superseded by RegisterPayloadHandlersEvent, a different mechanism
    "net.minecraftforge.network.PacketDistributor.PacketTarget",  # verified gone as a nested type (M6 S10); PacketDistributor itself is fine

    # items: no successor found under any mapped path.
    "net.minecraftforge.items.wrapper.EmptyHandler",

    # entity: no successor found under any mapped path.
    "net.minecraftforge.entity.IEntityAdditionalSpawnData",

    # event: five classes gone outright or absorbed into the
    # LivingHurtEvent -> LivingIncomingDamageEvent rework (M-brief note: only
    # the type itself is swept there, not these siblings).
    "net.minecraftforge.event.AttachCapabilitiesEvent",   # capability rework, see EXCLUDE_PREFIX above
    "net.minecraftforge.event.ForgeEventFactory",         # gone outright (M3 S4 unmatched list)
    "net.minecraftforge.event.TickEvent.Phase",           # TickEvent restructured into per-phase Pre/Post events
    "net.minecraftforge.event.TickEvent.PlayerTickEvent", # same restructure
    "net.minecraftforge.event.entity.SpawnPlacementRegisterEvent",            # verified MISSING under any mapped path
    "net.minecraftforge.event.entity.SpawnPlacementRegisterEvent.Operation",  # same
    "net.minecraftforge.event.entity.living.LivingAttackEvent",   # folded into the DamageContainer-based incoming-damage rework
    "net.minecraftforge.event.entity.living.LootingLevelEvent",   # looting is attribute-based now; no direct successor found
    "net.minecraftforge.event.entity.living.ShieldBlockEvent",    # verified MISSING under any mapped path
}

# ---------------------------------------------------------------------------
# 2. SPECIAL_RENAMES - class whose SIMPLE NAME also changed. Each entry fires
#    only in a file that contains the exact old import line; when it fires, it
#    rewrites that import line AND every whole-word occurrence of old_token
#    in the file (declarations, casts, static-field-qualified reads, etc.).
#    Method-body call sites naming the token are rewritten along with it -
#    that is the point (T3 brief S2: "signature TYPE mentions ... sweep too")
#    - but no method BODY is otherwise touched: argument lists, generic arity
#    and any semantics tied to the old shape are left exactly as they were for
#    the owning slice to fix.
# ---------------------------------------------------------------------------
SPECIAL_RENAMES = [
    dict(
        name="RegistryObject -> DeferredHolder",
        old_import="net.minecraftforge.registries.RegistryObject",
        new_import="net.neoforged.neoforge.registries.DeferredHolder",
        old_token="RegistryObject",
        new_token="DeferredHolder",
        # NOTE: DeferredHolder<R, T extends R> is two-parameter where
        # RegistryObject<T> was one (M3 S4). This transform renames the
        # identifier only; every generic argument list still reads
        # `DeferredHolder<Foo>` after this runs and needs a second type
        # argument added by hand. That is deliberate slice work, not this
        # script's job - see notes/T3-design.md's REWORK inventory.
    ),
    dict(
        name="ForgeConfigSpec -> ModConfigSpec",
        old_import="net.minecraftforge.common.ForgeConfigSpec",
        new_import="net.neoforged.neoforge.common.ModConfigSpec",
        old_token="ForgeConfigSpec",
        new_token="ModConfigSpec",
        # Nested Builder/BooleanValue/ConfigValue/DoubleValue/EnumValue/IntValue
        # keep their names (M3 S6), so the whole-word token rename alone
        # fixes every `ForgeConfigSpec.Builder` / `.BooleanValue` etc. mention.
    ),
    dict(
        name="ToolAction -> ItemAbility",
        old_import="net.minecraftforge.common.ToolAction",
        new_import="net.neoforged.neoforge.common.ItemAbility",
        old_token="ToolAction",
        new_token="ItemAbility",
        # ToolAction.get(String) and ItemAbility.get(String) are identical in
        # shape (verified against the 21.1.248 sources jar), so call sites
        # like `ToolAction.get("shield_disable")` become fully correct, not
        # just import-clean.
    ),
    dict(
        name="ToolActions -> ItemAbilities",
        old_import="net.minecraftforge.common.ToolActions",
        new_import="net.neoforged.neoforge.common.ItemAbilities",
        old_token="ToolActions",
        new_token="ItemAbilities",
        # Every vanilla constant Tinkers references (AXE_STRIP, HOE_TILL, ...)
        # keeps its exact name on ItemAbilities (verified), so
        # `ToolActions.AXE_STRIP` -> `ItemAbilities.AXE_STRIP` is exact, not
        # approximate.
    ),
    dict(
        name="MinecraftForge -> NeoForge",
        old_import="net.minecraftforge.common.MinecraftForge",
        new_import="net.neoforged.neoforge.common.NeoForge",
        old_token="MinecraftForge",
        new_token="NeoForge",
        # Every use in this codebase is `MinecraftForge.EVENT_BUS`, which
        # NeoForge.EVENT_BUS mirrors exactly (M3 S6). Gated on the import
        # being present, so a stray prose mention of "Minecraft Forge"
        # elsewhere is never touched.
    ),
    dict(
        name="ForgeFlowingFluid -> BaseFlowingFluid",
        old_import="net.minecraftforge.fluids.ForgeFlowingFluid",
        new_import="net.neoforged.neoforge.fluids.BaseFlowingFluid",
        old_token="ForgeFlowingFluid",
        new_token="BaseFlowingFluid",
        # Nested Properties/Source/Flowing keep their names (M3 S6).
    ),
    dict(
        name="ForgeSpawnEggItem -> DeferredSpawnEggItem",
        old_import="net.minecraftforge.common.ForgeSpawnEggItem",
        new_import="net.neoforged.neoforge.common.DeferredSpawnEggItem",
        old_token="ForgeSpawnEggItem",
        new_token="DeferredSpawnEggItem",
        # Constructor shape unchanged (M3 S6): (Supplier<EntityType<? extends
        # Mob>>, int, int, Item.Properties). No Tinkers file currently
        # imports this (0 hits at the time this script was written); kept in
        # the table per the brief's instruction to enumerate every mapping.
    ),
    dict(
        name="IForgeMenuType -> IMenuTypeExtension",
        old_import="net.minecraftforge.common.extensions.IForgeMenuType",
        new_import="net.neoforged.neoforge.common.extensions.IMenuTypeExtension",
        old_token="IForgeMenuType",
        new_token="IMenuTypeExtension",
        # create(IContainerFactory) static method unchanged (M3 S6).
    ),
    dict(
        name="LivingHurtEvent -> LivingIncomingDamageEvent",
        old_import="net.minecraftforge.event.entity.living.LivingHurtEvent",
        new_import="net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent",
        old_token="LivingHurtEvent",
        new_token="LivingIncomingDamageEvent",
        # Import and type mention ONLY, per the brief: the new event's
        # constructor takes a DamageContainer, not (entity, source, amount),
        # and there is no getAmount()/setAmount() at all (verified against
        # the sources jar) - the Pre/Post semantic split is real slice work.
        # Every `event.getAmount()`/`event.setAmount(...)` call in a body
        # this transform touches is left exactly as it reads today.
    ),
]

# ---------------------------------------------------------------------------
# 3. REGISTRY_FIELD_RENAMES - ForgeRegistries.<FIELD> is not a package rename:
#    BuiltInRegistries uses vanilla's singular field names (ITEM, not ITEMS;
#    BLOCK, not BLOCKS), verified via javap against
#    net.minecraft.core.registries.BuiltInRegistries in the compiled
#    NeoForge-patched classpath jar. ForgeRegistries.Keys.BIOME_MODIFIERS is
#    the one Keys member Tinkers actually uses that has a NeoForge-only
#    (not vanilla) target, NeoForgeRegistries.Keys.BIOME_MODIFIERS (verified
#    present in the 21.1.248 sources jar).
#
#    ForgeRegistries.ENCHANTMENTS and ForgeRegistries.Keys.DISPLAY_CONTEXTS
#    are deliberately absent from this table: enchantments are a datapack
#    registry with no BuiltInRegistries.ENCHANTMENT field in 1.21.1 (verified
#    absent via javap; T2's AT triage #108 already flags the wider
#    Enchantment rework), and DISPLAY_CONTEXTS has no registry key on
#    NeoForgeRegistries at all (verified absent). Both are REWORK, not rename;
#    both are also inside readRegistryIdUnsafe/writeRegistryIdUnsafe calls in
#    their one caller (UpdateModifiersPacket), i.e. squarely inside network
#    packet-body territory this script does not touch either way.
# ---------------------------------------------------------------------------
REGISTRY_FIELD_TO_BUILTIN = {
    "ITEMS": "ITEM",
    "BLOCKS": "BLOCK",
    "POTIONS": "POTION",
    "FLUIDS": "FLUID",
    "PARTICLE_TYPES": "PARTICLE_TYPE",
    "MOB_EFFECTS": "MOB_EFFECT",
    "ENTITY_TYPES": "ENTITY_TYPE",
    "ATTRIBUTES": "ATTRIBUTE",
    "SOUND_EVENTS": "SOUND_EVENT",
    "RECIPE_SERIALIZERS": "RECIPE_SERIALIZER",
    "FEATURES": "FEATURE",
    "BLOCK_ENTITY_TYPES": "BLOCK_ENTITY_TYPE",
}
REGISTRY_KEYS_FIELD_TO_NEOFORGE = {
    "BIOME_MODIFIERS": "BIOME_MODIFIERS",
}
BUILTIN_IMPORT = "net.minecraft.core.registries.BuiltInRegistries"
NEOFORGE_REGISTRIES_IMPORT = "net.neoforged.neoforge.registries.NeoForgeRegistries"
FORGE_REGISTRIES_IMPORT = "net.minecraftforge.registries.ForgeRegistries"

# ---------------------------------------------------------------------------
# 5. DROP_IMPORTS - SimpleChannel-era imports whose role Mantle's IPacket /
#    PacketContext path (M6) fully absorbs. Import line deleted, no
#    replacement added, method-body signatures that still name the old type
#    are untouched (M6's per-packet handler rewrite is slice work - T3 brief
#    S2: "handler bodies are slice work").
# ---------------------------------------------------------------------------
DROP_IMPORTS = {
    "net.minecraftforge.network.NetworkEvent.Context",
    "net.minecraftforge.network.NetworkEvent",
}

# ---------------------------------------------------------------------------
# Transform implementations
# ---------------------------------------------------------------------------

IMPORT_LINE_RE = re.compile(r"^(import(?:\s+static)?\s+)([\w.]+)(;\s*)$")


def split_import_target(fqcn: str) -> str:
    """An import target may be a member (static import) or a type; both are
    just dotted paths for our purposes, so this is the identity - kept as a
    named seam in case member-vs-type ever needs different handling."""
    return fqcn


def apply_package_renames(text: str, counts: Counter, guarded: set[str]) -> str:
    out_lines = []
    changed = False
    for line in text.split("\n"):
        m = IMPORT_LINE_RE.match(line)
        if not m:
            out_lines.append(line)
            continue
        prefix, target, suffix = m.groups()
        if not target.startswith("net.minecraftforge."):
            out_lines.append(line)
            continue
        if target in EXCLUDE_EXACT or any(target.startswith(p) for p in EXCLUDE_PREFIX):
            guarded.add(target)
            out_lines.append(line)
            continue
        if target in DROP_IMPORTS:
            # handled by drop_simplechannel_imports; leave for that pass
            out_lines.append(line)
            continue
        if any(target == s["old_import"] or target.startswith(s["old_import"] + ".") for s in SPECIAL_RENAMES):
            # handled by apply_special_renames; leave for that pass
            out_lines.append(line)
            continue
        if target == FORGE_REGISTRIES_IMPORT or target.startswith(FORGE_REGISTRIES_IMPORT + "."):
            # handled by apply_registry_field_renames; leave for that pass
            out_lines.append(line)
            continue
        for old_prefix, new_prefix in PACKAGE_RENAMES:
            if target.startswith(old_prefix):
                new_target = new_prefix + target[len(old_prefix):]
                out_lines.append(f"{prefix}{new_target}{suffix}")
                counts[f"package: {old_prefix} -> {new_prefix}"] += 1
                changed = True
                break
        else:
            # No rule matched at all - an import under net.minecraftforge we
            # have no evidence for. Leave untouched and surface it so a human
            # notices (should be empty; every import in this repo was
            # enumerated when this table was built).
            guarded.add(target)
            out_lines.append(line)
    return "\n".join(out_lines)


def apply_special_renames(text: str, counts: Counter) -> str:
    for spec in SPECIAL_RENAMES:
        old_import_line_re = re.compile(
            r"^import(?:\s+static)?\s+" + re.escape(spec["old_import"]) + r"\s*;\s*$",
            re.MULTILINE,
        )
        if not old_import_line_re.search(text):
            continue
        # Count every whole-word occurrence up front (import line included),
        # then perform the two substitutions - this keeps the reported count
        # equal to "how many times this identifier was rewritten in this
        # file", import line and body alike, rather than under-counting the
        # import line just because a different regex performs that one edit.
        token_re = re.compile(r"\b" + re.escape(spec["old_token"]) + r"\b")
        counts[f"special: {spec['name']}"] += len(token_re.findall(text))
        text = old_import_line_re.sub(
            f"import {spec['new_import']};", text
        )
        text = token_re.sub(spec["new_token"], text)
    return text


def drop_simplechannel_imports(text: str, counts: Counter) -> str:
    out_lines = []
    for line in text.split("\n"):
        m = IMPORT_LINE_RE.match(line)
        if m and m.group(2) in DROP_IMPORTS:
            counts[f"drop import: {m.group(2)}"] += 1
            continue
        out_lines.append(line)
    return "\n".join(out_lines)


FORGE_REGISTRIES_FIELD_RE = re.compile(r"\bForgeRegistries\.([A-Z_]+)\b")
FORGE_REGISTRIES_KEYS_RE = re.compile(r"\bForgeRegistries\.Keys\.([A-Z_]+)\b")
FORGE_REGISTRIES_BARE_RE = re.compile(r"\bForgeRegistries\b")


def apply_registry_field_renames(text: str, counts: Counter, guarded: set[str]) -> str:
    if "ForgeRegistries" not in text:
        return text
    import_line_re = re.compile(
        r"^import\s+" + re.escape(FORGE_REGISTRIES_IMPORT) + r"\s*;\s*$", re.MULTILINE
    )
    if not import_line_re.search(text):
        return text

    needs_builtin = False
    needs_neoforge_registries = False

    def replace_keys(match: "re.Match[str]") -> str:
        nonlocal needs_neoforge_registries
        field = match.group(1)
        target = REGISTRY_KEYS_FIELD_TO_NEOFORGE.get(field)
        if target is None:
            guarded.add(f"{FORGE_REGISTRIES_IMPORT}.Keys.{field}")
            return match.group(0)
        needs_neoforge_registries = True
        counts["registry field: ForgeRegistries.Keys.* -> NeoForgeRegistries.Keys.*"] += 1
        return f"NeoForgeRegistries.Keys.{target}"

    def replace_plain(match: "re.Match[str]") -> str:
        nonlocal needs_builtin
        field = match.group(1)
        target = REGISTRY_FIELD_TO_BUILTIN.get(field)
        if target is None:
            guarded.add(f"{FORGE_REGISTRIES_IMPORT}.{field}")
            return match.group(0)
        needs_builtin = True
        counts["registry field: ForgeRegistries.* -> BuiltInRegistries.*"] += 1
        return f"BuiltInRegistries.{target}"

    # Keys.* first (more specific pattern), then bare ForgeRegistries.FIELD.
    text = FORGE_REGISTRIES_KEYS_RE.sub(replace_keys, text)
    text = FORGE_REGISTRIES_FIELD_RE.sub(replace_plain, text)

    # "Still used" must be judged on the BODY only - the import line itself
    # still reads "...ForgeRegistries;" at this point (it hasn't been
    # rewritten yet), so checking the whole text would always find a match
    # and never take the "replace the import outright" branch below.
    body_without_import = import_line_re.sub("", text, count=1)
    still_used = bool(FORGE_REGISTRIES_BARE_RE.search(body_without_import))

    new_import_lines = []
    if needs_builtin:
        new_import_lines.append(f"import {BUILTIN_IMPORT};")
    if needs_neoforge_registries:
        new_import_lines.append(f"import {NEOFORGE_REGISTRIES_IMPORT};")

    def already_imports(target: str) -> bool:
        return re.search(r"^import\s+" + re.escape(target) + r"\s*;\s*$", text, re.MULTILINE) is not None

    new_import_lines = [l for l in new_import_lines if not already_imports(l[len("import "):-1])]

    if still_used:
        # Leftover unmapped ForgeRegistries.* reference (e.g. ENCHANTMENTS) -
        # keep the old import (still needed) and ADD whichever new imports
        # the mapped fields in this file now need.
        if new_import_lines:
            text = import_line_re.sub(
                lambda m: m.group(0) + "\n" + "\n".join(new_import_lines), text, count=1
            )
    else:
        # Every ForgeRegistries.* reference in the file was mapped - replace
        # the old import outright.
        if new_import_lines:
            text = import_line_re.sub("\n".join(new_import_lines), text, count=1)
        else:
            text = import_line_re.sub("", text, count=1)

    return text


# --- ResourceLocation constructor rewrite ----------------------------------

def _find_matching_paren(text: str, open_idx: int) -> int:
    """text[open_idx] == '('; returns index of the matching ')', skipping
    over nested parens/brackets/braces and string/char literals."""
    depth = 0
    i = open_idx
    n = len(text)
    while i < n:
        c = text[i]
        if c == '"':
            i += 1
            while i < n and text[i] != '"':
                if text[i] == "\\":
                    i += 1
                i += 1
        elif c == "'":
            i += 1
            while i < n and text[i] != "'":
                if text[i] == "\\":
                    i += 1
                i += 1
        elif c in "([{":
            depth += 1
        elif c in ")]}":
            depth -= 1
            if depth == 0:
                return i
        i += 1
    raise ValueError("unbalanced parens")


def _split_top_level_args(arg_text: str) -> list[str]:
    if arg_text.strip() == "":
        return []
    parts = []
    depth = 0
    start = 0
    i = 0
    n = len(arg_text)
    while i < n:
        c = arg_text[i]
        if c == '"':
            i += 1
            while i < n and arg_text[i] != '"':
                if arg_text[i] == "\\":
                    i += 1
                i += 1
        elif c == "'":
            i += 1
            while i < n and arg_text[i] != "'":
                if arg_text[i] == "\\":
                    i += 1
                i += 1
        elif c in "([{":
            depth += 1
        elif c in ")]}":
            depth -= 1
        elif c == "," and depth == 0:
            parts.append(arg_text[start:i])
            start = i + 1
        i += 1
    parts.append(arg_text[start:])
    return [p.strip() for p in parts]


NEW_RESOURCE_LOCATION_RE = re.compile(r"\bnew\s+ResourceLocation\s*\(")


def apply_resource_location_ctor(text: str, counts: Counter, guarded: set[str]) -> str:
    out = []
    pos = 0
    for m in NEW_RESOURCE_LOCATION_RE.finditer(text):
        if m.start() < pos:
            continue  # inside a span already emitted
        open_idx = m.end() - 1
        try:
            close_idx = _find_matching_paren(text, open_idx)
        except ValueError:
            continue
        out.append(text[pos:m.start()])
        arg_text = text[open_idx + 1:close_idx]
        args = _split_top_level_args(arg_text)
        if len(args) == 2:
            out.append(f"ResourceLocation.fromNamespaceAndPath({args[0]}, {args[1]})")
            counts["ResourceLocation: new(ns, path) -> fromNamespaceAndPath"] += 1
        elif len(args) == 1:
            out.append(f"ResourceLocation.parse({args[0]})")
            counts["ResourceLocation: new(s) -> parse"] += 1
        else:
            # 0 or 3+ args: not a shape this constructor has today. Leave
            # untouched and flag - should not occur in practice.
            guarded.add(f"new ResourceLocation(<{len(args)} args>)")
            out.append(text[m.start():close_idx + 1])
        pos = close_idx + 1
    out.append(text[pos:])
    return "".join(out)


# ---------------------------------------------------------------------------
# Driver
# ---------------------------------------------------------------------------

def process_file(path: pathlib.Path, counts: Counter, guarded: set[str]) -> bool:
    original = path.read_text(encoding="utf-8")
    text = original
    text = apply_package_renames(text, counts, guarded)
    text = apply_special_renames(text, counts)
    text = apply_registry_field_renames(text, counts, guarded)
    text = drop_simplechannel_imports(text, counts)
    text = apply_resource_location_ctor(text, counts, guarded)
    if text != original:
        path.write_text(text, encoding="utf-8")
        return True
    return False


def is_do_not_touch(path: pathlib.Path) -> bool:
    p = "/" + str(path.relative_to(REPO_ROOT)).replace("\\", "/")
    return any(guard in p for guard in DO_NOT_TOUCH_DIRS)


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--dry-run", action="store_true")
    ap.add_argument("--check", action="store_true")
    args = ap.parse_args()

    all_files = []
    for root in SOURCE_ROOTS:
        if root.exists():
            all_files.extend(sorted(root.rglob("*.java")))

    skipped_files = [f for f in all_files if is_do_not_touch(f)]
    swept_files = [f for f in all_files if not is_do_not_touch(f)]

    counts: Counter[str] = Counter()
    guarded: set[str] = set()
    guard_files: dict[str, set[str]] = defaultdict(set)
    changed_files = []

    for f in swept_files:
        before_counts = counts.copy()
        before_guarded = set(guarded)
        original = f.read_text(encoding="utf-8")
        text = original
        text = apply_package_renames(text, counts, guarded)
        text = apply_special_renames(text, counts)
        text = apply_registry_field_renames(text, counts, guarded)
        text = drop_simplechannel_imports(text, counts)
        text = apply_resource_location_ctor(text, counts, guarded)
        newly_guarded = guarded - before_guarded
        for g in newly_guarded:
            guard_files[g].add(str(f.relative_to(REPO_ROOT)))
        if text != original:
            changed_files.append(f)
            if not args.dry_run and not args.check:
                f.write_text(text, encoding="utf-8")

    print(f"Scanned {len(all_files)} .java files under src/{{main,test,gametest}}/java.")
    print(f"Skipped (do-not-touch dirs): {len(skipped_files)} files.")
    print(f"Swept: {len(swept_files)} files, {len(changed_files)} changed.\n")

    print("Per-transform hit count:")
    width = max((len(k) for k in counts), default=10)
    for k in sorted(counts):
        print(f"  {k.ljust(width)}  {counts[k]}")
    total_hits = sum(counts.values())
    print(f"  {'TOTAL'.ljust(width)}  {total_hits}\n")

    print(f"Guarded symbols encountered (never rewritten): {len(guard_files)}")
    for g in sorted(guard_files):
        files = sorted(guard_files[g])
        print(f"  {g}  ({len(files)} file(s))")

    print(f"\nDo-not-touch files (whole-file skip): {len(skipped_files)}")
    for f in sorted(skipped_files):
        print(f"  {f.relative_to(REPO_ROOT)}")

    if args.check:
        if changed_files:
            print(f"\n--check: sweep is NOT a no-op, {len(changed_files)} file(s) would change:")
            for f in changed_files:
                print(f"  {f.relative_to(REPO_ROOT)}")
            return 1
        print("\n--check: idempotent, zero files would change.")
        return 0

    if args.dry_run:
        print(f"\n--dry-run: {len(changed_files)} file(s) would change; nothing written.")
        return 0

    print(f"\nWrote changes to {len(changed_files)} file(s).")
    return 0


if __name__ == "__main__":
    sys.exit(main())
