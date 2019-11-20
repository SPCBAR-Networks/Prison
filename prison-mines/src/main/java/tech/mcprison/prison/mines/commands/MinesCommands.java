/*
 * Prison is a Minecraft plugin for the prison game mode.
 * Copyright (C) 2017 The Prison Team
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package tech.mcprison.prison.mines.commands;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.apache.commons.lang3.StringUtils;

import tech.mcprison.prison.Prison;
import tech.mcprison.prison.chat.FancyMessage;
import tech.mcprison.prison.commands.Arg;
import tech.mcprison.prison.commands.Command;
import tech.mcprison.prison.internal.CommandSender;
import tech.mcprison.prison.internal.Player;
import tech.mcprison.prison.localization.Localizable;
import tech.mcprison.prison.mines.PrisonMines;
import tech.mcprison.prison.mines.data.Block;
import tech.mcprison.prison.mines.data.Mine;
import tech.mcprison.prison.output.BulletedListComponent;
import tech.mcprison.prison.output.ButtonComponent;
import tech.mcprison.prison.output.ButtonComponent.Style;
import tech.mcprison.prison.output.ChatDisplay;
import tech.mcprison.prison.output.Output;
import tech.mcprison.prison.output.RowComponent;
import tech.mcprison.prison.selection.Selection;
import tech.mcprison.prison.util.BlockType;

/**
 * @author Dylan M. Perks
 */
public class MinesCommands {

    private boolean performCheckMineExists(CommandSender sender, String name) {
        if (!PrisonMines.getInstance().getMineManager().getMine(name).isPresent()) {
            PrisonMines.getInstance().getMinesMessages().getLocalizable("mine_does_not_exist")
                .sendTo(sender);
            return false;
        }
        return true;
    }

    @Command(identifier = "mines create", description = "Creates a new mine.", permissions = "mines.create")
    public void createCommand(CommandSender sender,
        @Arg(name = "mineName", description = "The name of the new mine.") String name) {

        Selection selection = Prison.get().getSelectionManager().getSelection((Player) sender);
        if (!selection.isComplete()) {
            PrisonMines.getInstance().getMinesMessages().getLocalizable("select_bounds")
                .sendTo(sender, Localizable.Level.ERROR);
            return;
        }

        if (!selection.getMin().getWorld().getName()
            .equalsIgnoreCase(selection.getMax().getWorld().getName())) {
            PrisonMines.getInstance().getMinesMessages().getLocalizable("world_diff")
                .sendTo(sender, Localizable.Level.ERROR);
            return;
        }

        if (PrisonMines.getInstance().getMines().stream()
            .anyMatch(mine -> mine.getName().equalsIgnoreCase(name))) {
            PrisonMines.getInstance().getMinesMessages().getLocalizable("mine_exists")
                .sendTo(sender, Localizable.Level.ERROR);
            return;
        }

        Mine mine = new Mine().setBounds(selection.asBounds()).setName(name);
        PrisonMines.getInstance().getMineManager().add(mine);
        PrisonMines.getInstance().getMinesMessages().getLocalizable("mine_created").sendTo(sender);
    }

    @Command(identifier = "mines set spawn", description = "Set the mine's spawn to where you're standing.", permissions = "mines.set")
    public void spawnpointCommand(CommandSender sender,
        @Arg(name = "mineName", description = "The name of the mine to edit.") String name) {

        if (!performCheckMineExists(sender, name)) {
            return;
        }

        if (!PrisonMines.getInstance().getMineManager().getMine(name).get().getWorld()
            .isPresent()) {
            PrisonMines.getInstance().getMinesMessages().getLocalizable("missing_world")
                .sendTo(sender);
            return;
        }

        if (!((Player) sender).getLocation().getWorld().getName()
            .equalsIgnoreCase(
                PrisonMines.getInstance().getMineManager().getMine(name).get().getWorld().get()
                    .getName())) {
            PrisonMines.getInstance().getMinesMessages().getLocalizable("spawnpoint_same_world")
                .sendTo(sender);
            return;
        }

        PrisonMines.getInstance().getMineManager().getMine(name).get()
            .setSpawn(((Player) sender).getLocation());
        PrisonMines.getInstance().getMinesMessages().getLocalizable("spawn_set").sendTo(sender);
    }

    @Command(identifier = "mines block add", permissions = "mines.block", onlyPlayers = false, description = "Adds a block to a mine.")
    public void addBlockCommand(CommandSender sender,
    			@Arg(name = "mineName", description = "The name of the mine to add the block to.")
            			String mine, 
            	@Arg(name = "block", description = "The block's name or ID.") 
    					String block,
            	@Arg(name = "chance", description = "The percent chance (out of 100) that this block will occur.")
    					double chance) {
        if (!performCheckMineExists(sender, mine)) {
            return;
        }

        Mine m = PrisonMines.getInstance().getMineManager().getMine(mine).get();

        BlockType blockType = BlockType.getBlock(block);
        if (blockType == null) {
            PrisonMines.getInstance().getMinesMessages().getLocalizable("not_a_block")
                .withReplacements(block).sendTo(sender);
            return;
        }

        if (PrisonMines.getInstance().getMineManager().getMine(mine).get().isInMine(blockType)) {
            PrisonMines.getInstance().getMinesMessages().getLocalizable("block_already_added")
                .sendTo(sender);
            return;
        }

        if ( chance <= 0 ) {
        	sender.sendMessage( "The percent chance must have a value greater than zero." );
        	return;
        }
        
        final double[] totalComp = {chance};
        m.getBlocks().forEach(block1 -> totalComp[0] += block1.getChance());
        if (totalComp[0] > 100.0d) {
            PrisonMines.getInstance().getMinesMessages().getLocalizable("mine_full")
                .sendTo(sender, Localizable.Level.ERROR);
            return;
        }

        m.getBlocks().add(new Block(blockType, chance));
        PrisonMines.getInstance().getMinesMessages().getLocalizable("block_added")
            .withReplacements(block, mine).sendTo(sender);
        getBlocksList(m).send(sender);

        PrisonMines.getInstance().getMineManager().clearCache();
    }

    @Command(identifier = "mines block set", permissions = "mines.block", onlyPlayers = false, description = "Changes the percentage of a block in a mine.")
    public void setBlockCommand(CommandSender sender,
        @Arg(name = "mineName", description = "The name of the mine to edit.") String mine,
        @Arg(name = "block", description = "The block's name or ID.") String block,
        @Arg(name = "chance", description = "The percent chance (out of 100) that this block will occur.")
            double chance) {
        if (!performCheckMineExists(sender, mine)) {
            return;
        }

        Mine m = PrisonMines.getInstance().getMineManager().getMine(mine).get();

        BlockType blockType = BlockType.getBlock(block);
        if (blockType == null) {
            PrisonMines.getInstance().getMinesMessages().getLocalizable("not_a_block")
                .withReplacements(block).sendTo(sender);
            return;
        }

        if (!PrisonMines.getInstance().getMineManager().getMine(mine).get().isInMine(blockType)) {
            PrisonMines.getInstance().getMinesMessages().getLocalizable("block_not_removed")
                .sendTo(sender);
            return;
        }

        // If it's 0, just delete it!
        if (chance <= 0.0d) {
            delBlockCommand(sender, mine, block);
            return;
        }

        final double[] totalComp = {chance};
        m.getBlocks().forEach(block1 -> {
            if (block1.getType() == blockType) {
                totalComp[0] -= block1.getChance();
            } else {
                totalComp[0] += block1.getChance();
            }
        });
        if (totalComp[0] > 100.0d) {
            PrisonMines.getInstance().getMinesMessages().getLocalizable("mine_full")
                .sendTo(sender, Localizable.Level.ERROR);
            return;
        }

        for (Block blockObject : PrisonMines.getInstance().getMineManager().getMine(mine).get()
            .getBlocks()) {
            if (blockObject.getType() == blockType) {
                blockObject.setChance(chance);
            }
        }

        PrisonMines.getInstance().getMinesMessages().getLocalizable("block_set")
            .withReplacements(block, mine).sendTo(sender);
        getBlocksList(m).send(sender);

        PrisonMines.getInstance().getMineManager().clearCache();

    }

    @Command(identifier = "mines block remove", permissions = "mines.block", onlyPlayers = false, description = "Deletes a block from a mine.")
    public void delBlockCommand(CommandSender sender,
        @Arg(name = "mineName", description = "The name of the mine to edit.") String mine,
        @Arg(name = "block", def = "AIR", description = "The block's name or ID.") String block) {

        if (!performCheckMineExists(sender, mine)) {
            return;
        }

        BlockType blockType = BlockType.getBlock(block);
        if (blockType == null) {
            PrisonMines.getInstance().getMinesMessages().getLocalizable("not_a_block")
                .withReplacements(block).sendTo(sender);
            return;
        }

        if (!PrisonMines.getInstance().getMineManager().getMine(mine).get().isInMine(blockType)) {
            PrisonMines.getInstance().getMinesMessages().getLocalizable("block_not_removed")
                .sendTo(sender);
            return;
        }

        Mine m = PrisonMines.getInstance().getMineManager().getMine(mine).get();
        m.getBlocks().removeIf(x -> x.getType() == blockType);
        PrisonMines.getInstance().getMinesMessages().getLocalizable("block_deleted")
            .withReplacements(block, mine).sendTo(sender);
        getBlocksList(m).send(sender);

        PrisonMines.getInstance().getMineManager().clearCache();
    }

    @Command(identifier = "mines block search", permissions = "mines.block", description = "Searches for a block to add to a mine.")
    public void searchBlockCommand(CommandSender sender,
        @Arg(name = "search", def = " ", description = "Any part of the block's name or ID.") String search,
        @Arg(name = "page", def = "1", description = "Page of search results (optional)") String page ) {

    	if (search == null)
    	{
    		PrisonMines.getInstance().getMinesMessages().getLocalizable("block_search_blank").sendTo(sender);
    	}
    	
    	ChatDisplay display = blockSearchBuilder(search, page);
        
        display.send(sender);

        PrisonMines.getInstance().getMineManager().clearCache();
    }

	private ChatDisplay blockSearchBuilder(String search, String page)
	{
		List<BlockType> blocks = new ArrayList<>();
    	for (BlockType block : BlockType.values())
		{
			if (block.getId().contains(search.toLowerCase()) || 
					block.name().toLowerCase().contains(search.toLowerCase()))
			{
				blocks.add(block);
			}
		}
    	
    	int curPage = 1;
    	int pageSize = 10;
    	int pages = (blocks.size() / pageSize) + 1;
    	try
		{
			curPage = Integer.parseInt(page);
		}
		catch ( NumberFormatException e )
		{
			// Ignore: Not an integer, will use the default value.
		}
    	curPage = ( curPage < 1 ? 1 : (curPage > pages ? pages : curPage ));
    	int pageStart = (curPage - 1) * pageSize;
    	int pageEnd = ((pageStart + pageSize) > blocks.size() ? blocks.size() : pageStart + pageSize);

    	
        ChatDisplay display = new ChatDisplay("Block Search (" + blocks.size() + ")");
        display.text("&8Click a block to add it to a mine.");
        
        BulletedListComponent.BulletedListBuilder builder =
        						new BulletedListComponent.BulletedListBuilder();
        for ( int i = pageStart; i < pageEnd; i++ )
        {
        	BlockType block = blocks.get(i);
            FancyMessage msg =
                    new FancyMessage(
                    		String.format("&7%s %s - (%s)", 
                    				Integer.toString(i), block.name(), block.getId().replace("minecraft:", "")))
                    .suggest("/mines block add <mine> " + block.name() + " %")
                        .tooltip("&7Click to add block to a mine.");
                builder.add(msg);
        }
        display.addComponent(builder.build());
        
        // Need to construct a dynamic row of buttons. It may have no buttons, both, or
        // a combination of previous page or next page.  But it will always have a page
        // count between the two.
        RowComponent row = new RowComponent();
        if ( curPage > 1 )
        {
        	row.addFancy( 
        			new ButtonComponent( "&e<-- Prev Page", '-', Style.NEGATIVE)
        			.runCommand("/mines block search " + search + " " + (curPage - 1), 
        					"View the prior page of search results").getFancyMessage() );
        }
        row.addFancy( 
        		new FancyMessage(" &9< &3Page " + curPage + " of " + pages + " &9> ") );
        if ( curPage < pages )
        {
   			row.addFancy( 
        			new ButtonComponent( "&eNext Page -->", '+', Style.POSITIVE)
        			.runCommand("/mines block search " + search + " " + (curPage + 1), 
        					"View the prior page of search results").getFancyMessage() );
        }
        display.addComponent( row );
        
		return display;
	}


    @Command(identifier = "mines delete", permissions = "mines.delete", onlyPlayers = false, description = "Deletes a mine.")
    public void deleteCommand(CommandSender sender,
        @Arg(name = "mineName", description = "The name of the mine to delete.") String name) {
        if (!performCheckMineExists(sender, name)) {
            return;
        }

        PrisonMines.getInstance().getMineManager()
            .removeMine(PrisonMines.getInstance().getMineManager().getMine(name).get());
        PrisonMines.getInstance().getMinesMessages().getLocalizable("mine_deleted").sendTo(sender);
    }

    @Command(identifier = "mines info", permissions = "mines.info", onlyPlayers = false, description = "Lists information about a mine.")
    public void infoCommand(CommandSender sender,
        @Arg(name = "mineName", description = "The name of the mine to view.") String name) {
        if (!performCheckMineExists(sender, name)) {
            return;
        }

        Mine m = PrisonMines.getInstance().getMineManager().getMine(name).get();

        ChatDisplay chatDisplay = new ChatDisplay(m.getName());

        String worldName = m.getWorld().isPresent() ? m.getWorld().get().getName() : "&cmissing";
        chatDisplay.text("&3World: &7%s", worldName);

        String minCoords = m.getBounds().getMin().toBlockCoordinates();
        String maxCoords = m.getBounds().getMax().toBlockCoordinates();
        chatDisplay.text("&3Bounds: &7%s &8to &7%s", minCoords, maxCoords);

        chatDisplay.text("&3Size: &7%d&8x&7%d&8x&7%d", Math.round(m.getBounds().getWidth()),
            Math.round(m.getBounds().getHeight()), Math.round(m.getBounds().getLength()));

        String spawnPoint = m.getSpawn() != null ? m.getSpawn().toBlockCoordinates() : "&cnot set";
        chatDisplay.text("&3Spawnpoint: &7%s", spawnPoint);

        chatDisplay.text("&3Blocks:");
        chatDisplay.text("&8Click on a block's name to edit its chances of appearing.");
        BulletedListComponent list = getBlocksList(m);
        chatDisplay.addComponent(list);

        chatDisplay.send(sender);
    }

    private BulletedListComponent getBlocksList(Mine m) {
        BulletedListComponent.BulletedListBuilder builder =
            new BulletedListComponent.BulletedListBuilder();

        DecimalFormat dFmt = new DecimalFormat("##0.00");
        double totalChance = 0.0d;
        for (Block block : m.getBlocks()) {
            double chance = Math.round(block.getChance() * 100.0d) / 100.0d;
            totalChance += chance;

            String blockName =
                StringUtils.capitalize(block.getType().name().replaceAll("_", " ").toLowerCase());
            String percent = dFmt.format(chance) + "%";
            FancyMessage msg = new FancyMessage(String.format("&7%s - %s", percent, blockName))
                .suggest("/mines block set " + m.getName() + " " + block.getType().getId()
                    .replace("minecraft:", "") + " %")
                .tooltip("&7Click to edit the block's chance.");
            builder.add(msg);
        }

        if (totalChance < 100.0d) {
            builder.add("&e%s - Air", dFmt.format(100.0d - totalChance) + "%");
        }

        return builder.build();
    }

    @Command(identifier = "mines reset", permissions = "mines.reset", description = "Resets a mine.")
    public void resetCommand(CommandSender sender,
        @Arg(name = "mineName", description = "The name of the mine to reset.") String name) {

        if (!performCheckMineExists(sender, name)) {
            return;
        }

        try {
            PrisonMines.getInstance().getMineManager().getMine(name).get().reset();
        } catch (Exception e) {
            PrisonMines.getInstance().getMinesMessages().getLocalizable("mine_reset_fail")
                .sendTo(sender);
            Output.get().logError("Couldn't reset mine " + name, e);
        }

        PrisonMines.getInstance().getMinesMessages().getLocalizable("mine_reset").sendTo(sender);
    }


    @Command(identifier = "mines list", permissions = "mines.list", onlyPlayers = false)
    public void listCommand(CommandSender sender) {
        ChatDisplay display = new ChatDisplay("Mines");
        display.text("&8Click a mine's name to see more information.");
        BulletedListComponent.BulletedListBuilder builder =
            new BulletedListComponent.BulletedListBuilder();

        for (Mine m : PrisonMines.getInstance().getMines()) {
            FancyMessage msg =
                new FancyMessage("&7" + m.getName()).command("/mines info " + m.getName())
                    .tooltip("&7Click to view info.");
            builder.add(msg);
        }
        display.addComponent(builder.build());
        display.send(sender);
    }


    @Command(identifier = "mines set area", permissions = "mines.set", description = "Set the area of a mine to your current selection.")
    public void redefineCommand(CommandSender sender,
        @Arg(name = "mineName", description = "The name of the mine to edit.") String name) {

        Selection selection = Prison.get().getSelectionManager().getSelection((Player) sender);
        if (!selection.isComplete()) {
            PrisonMines.getInstance().getMinesMessages().getLocalizable("select_bounds")
                .sendTo(sender);
            return;
        }

        if (!Objects.equals(selection.getMin().getWorld().getName(),
            selection.getMax().getWorld().getName())) {
            PrisonMines.getInstance().getMinesMessages().getLocalizable("world_diff")
                .sendTo(sender);
            return;
        }

        if (!performCheckMineExists(sender, name)) {
            return;
        }

        // TODO check to see if they are the same boundaries, if not, don't change...
        
        PrisonMines.getInstance().getMineManager().getMine(name).get()
            .setBounds(selection.asBounds());
        PrisonMines.getInstance().getMinesMessages().getLocalizable("mine_redefined")
            .sendTo(sender);
        PrisonMines.getInstance().getMineManager().clearCache();
    }

    @Command(identifier = "mines wand", permissions = "mines.wand", description = "Receive a wand to select a mine area.")
    public void wandCommand(Player sender) {
        Prison.get().getSelectionManager().bestowSelectionTool(sender);
        sender.sendMessage(
            "&3Here you go! &7Left click to select the first corner, and right click to select the other.");
    }

}
