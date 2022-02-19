
package net.mcreator.findblocks.itemgroup;

import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.api.distmarker.Dist;

import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemGroup;

import net.mcreator.findblocks.block.TepoliumBlockBlock;
import net.mcreator.findblocks.FindblocksModElements;

@FindblocksModElements.ModElement.Tag
public class SPHItemGroup extends FindblocksModElements.ModElement {
	public SPHItemGroup(FindblocksModElements instance) {
		super(instance, 15);
	}

	@Override
	public void initElements() {
		tab = new ItemGroup("tabsph") {
			@OnlyIn(Dist.CLIENT)
			@Override
			public ItemStack createIcon() {
				return new ItemStack(TepoliumBlockBlock.block);
			}

			@OnlyIn(Dist.CLIENT)
			public boolean hasSearchBar() {
				return false;
			}
		};
	}
	public static ItemGroup tab;
}
