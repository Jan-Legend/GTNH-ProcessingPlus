package com.gtnh.processingplus.recipes;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import com.gtnh.processingplus.GTNHProcessingPlus;
import com.gtnh.processingplus.materials.PrPMaterials;

import goodgenerator.api.recipe.GoodGeneratorRecipeMaps;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.objects.ItemData;
import gregtech.api.recipe.RecipeMaps;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTUtility;

/**
 * Gates select endgame recipes behind the LuV-tier Vibranium chain by swapping a little Naquadah
 * Alloy for a smaller amount of Vibranium — surgically, not wholesale:
 * <ul>
 * <li><b>Component Assembly Line</b>: only the ZPM <b>Electric Motor</b> (every other component
 * consumes motors, so this one gate propagates to the whole line) gets its molten Naq Alloy
 * partly replaced.</li>
 * <li><b>Assembly Line</b>: the <b>Fusion Reactor Mk1</b> (FusionComputer_LuV) gets its Naquadah
 * Alloy dense plates swapped for a smaller amount of Vibranium dense plates.</li>
 * </ul>
 *
 * <p>
 * Must run after GoodGenerator + GT have registered their recipes — call from {@code loadComplete}.
 */
public final class VibraniumComponentSwap {

    /** GoodGenerator Component Assembly Line casing-tier special value for ZPM. */
    private static final int COAL_ZPM = 7;

    private VibraniumComponentSwap() {}

    public static void run() {
        swapComponentAssemblyLineMotor();
        swapFusionReactorMk1();
    }

    // ── Component Assembly Line: ZPM Electric Motor only ──
    private static void swapComponentAssemblyLineMotor() {
        FluidStack naqMolten = Materials.NaquadahAlloy.getMolten(1);
        ItemStack motor = ItemList.Electric_Motor_ZPM.get(1);
        if (naqMolten == null) return;

        List<GTRecipe> snapshot = new ArrayList<>(GoodGeneratorRecipeMaps.componentAssemblyLineRecipes.getAllRecipes());
        List<GTRecipe> toRemove = new ArrayList<>();
        List<GTRecipe> toAdd = new ArrayList<>();

        for (GTRecipe r : snapshot) {
            if (r.mSpecialValue != COAL_ZPM) continue;
            if (r.mOutputs == null || r.mOutputs.length == 0 || r.mOutputs[0] == null) continue;
            if (!GTUtility.areStacksEqual(r.mOutputs[0], motor)) continue; // motor only

            GTRecipe copy = r.copy();
            if (copy.mFluidInputs != null) copy.mFluidInputs = copy.mFluidInputs.clone();
            boolean changed = false;
            if (copy.mFluidInputs != null) {
                for (int i = 0; i < copy.mFluidInputs.length; i++) {
                    FluidStack f = copy.mFluidInputs[i];
                    if (f != null && f.getFluid() == naqMolten.getFluid()) {
                        FluidStack vib = PrPMaterials.Vibranium.getMolten(Math.max(144, f.amount / 4));
                        if (vib != null) {
                            copy.mFluidInputs[i] = vib;
                            changed = true;
                        }
                    }
                }
            }
            if (changed) {
                toRemove.add(r);
                toAdd.add(copy);
            }
        }
        GoodGeneratorRecipeMaps.componentAssemblyLineRecipes.getBackend()
            .removeRecipes(toRemove);
        for (GTRecipe a : toAdd) {
            GoodGeneratorRecipeMaps.componentAssemblyLineRecipes.addRecipe(a);
        }
        GTNHProcessingPlus.LOG.info("Vibranium: swapped {} ZPM Electric Motor (component line) recipes.", toAdd.size());
    }

    // ── Assembly Line: Fusion Reactor Mk1 — Naq Alloy dense plates -> Vibranium dense plates ──
    private static void swapFusionReactorMk1() {
        ItemStack vibPlate = PrPMaterials.Vibranium.get(OrePrefixes.plateDense, 2);
        if (vibPlate == null) return;
        ItemStack output = ItemList.FusionComputer_LuV.get(1);

        // Real recipes (what the machine checks): RecipeAssemblyLine has its own (non-GTRecipe) type.
        int n = 0;
        for (GTRecipe.RecipeAssemblyLine r : GTRecipe.RecipeAssemblyLine.sAssemblylineRecipes) {
            if (r.mOutput == null || !GTUtility.areStacksEqual(r.mOutput, output)) continue;
            n += swapNaqDensePlate(r.mInputs, vibPlate);
        }
        // NEI visual recipes (kept in sync so the displayed inputs match).
        for (GTRecipe r : RecipeMaps.assemblylineVisualRecipes.getAllRecipes()) {
            if (r.mOutputs == null || r.mOutputs.length == 0 || r.mOutputs[0] == null) continue;
            if (!GTUtility.areStacksEqual(r.mOutputs[0], output)) continue;
            swapNaqDensePlate(r.mInputs, vibPlate);
        }
        GTNHProcessingPlus.LOG.info("Vibranium: swapped Naq Alloy dense plates in {} Fusion Mk1 recipe(s).", n);
    }

    /** Replace any Naquadah Alloy dense-plate stack in the input array with the given Vibranium stack. */
    private static int swapNaqDensePlate(ItemStack[] inputs, ItemStack vibPlate) {
        if (inputs == null) return 0;
        int count = 0;
        for (int i = 0; i < inputs.length; i++) {
            ItemStack s = inputs[i];
            if (s == null) continue;
            ItemData d = GTOreDictUnificator.getAssociation(s);
            if (d != null && d.mPrefix == OrePrefixes.plateDense
                && d.mMaterial != null
                && d.mMaterial.mMaterial == Materials.NaquadahAlloy) {
                inputs[i] = vibPlate.copy();
                count++;
            }
        }
        return count;
    }
}
