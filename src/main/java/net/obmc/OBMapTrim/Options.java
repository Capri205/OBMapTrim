package net.obmc.OBMapTrim;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class Options {

	private String mapPath = null;
	private int radius = 3;
	// legacy: 19,27,35,44,57,80,87,88,91,95,123,124,133,138,152
	private List<String> defaultPreserveBlocks = new ArrayList<String>(
		List.of( "minecraft:sponge", "minecraft:powered_rail", "minecraft:white_wool", "minecraft:smooth_stone_slab", "minecraft:anvil",
				 "minecraft:redstone_lamp", "minecraft:lit_redstone_lamp", "minecraft:sea_lantern", "minecraft:beacon", "minecraft:redstone_block", 
				 "minecraft:emerald_block", "minecraft:barrier"
		)
	);
	private List<String> preserveBlocks = new ArrayList<String>();
	private boolean status = true;
	
	public void ReadArgs( String[] args ) {
		
		// process command line arguments
		for( int i=0; i<args.length; ++i ) {
			
			// get world path
			if (args[i].equals("-w") ) {
				
				if ( ++i >= args.length )
					break;
				
				this.mapPath = args[i];
				
				if ( !Files.exists( Paths.get( this.mapPath ) ) ) {

					OBMapTrim.outputMsg( "World directory '" + this.mapPath + "' does not exist or is inaccessible\n", false, true, true );
					this.status = false;

					return;
				}
				
			// get radius value
			} else if ( args[i].equals( "-r" ) ) {
				
				if( ++i >= args.length )
					break;
				
				try {
					
					int pd = Integer.parseInt( args[i] );
					if( pd >= 0 ) {
						this.radius = pd;
					}

				} catch (NumberFormatException e) {
					OBMapTrim.outputMsg( "Unable to parse provided radius\n", false, true, true );
					this.status = false;
				}

			// get list of preserve blocks
			} else if ( args[i].equals( "-p" ) ) {

				if ( ++i >= args.length )
					break;
				
				String[] blocks = args[i].split( "," );

				try {
						
					preserveBlocks = new ArrayList<>( Arrays.asList( blocks ) );
					OBMapTrim.outputMsg( "Using provided preserve block set\n", true, false, true );
						
				} catch ( Exception e ) {
						
					preserveBlocks = null;
					OBMapTrim.outputMsg( "Unable to parse preserve blocks\n", false, true, true );
					this.status = false;
				}
			} else {

				OBMapTrim.outputMsg( "Unknown option " + args[i] + "\n", true, false, true );
				this.status = false;
			}
		}

		OBMapTrim.outputMsg( "Blocks in preserve set: " + preserveBlocks.size() + "\n", true, false, true );

		// set default preserve block list if none provided
		if ( preserveBlocks.size() == 0 && this.status == true ) {
			
			OBMapTrim.outputMsg( "Using default preserve block list\n", true, false, true);
			preserveBlocks = defaultPreserveBlocks;
		}

		this.status = true;
	}

	public String getMapPath() {

		return this.mapPath;
	}
	
	public int getRadius() {
		return this.radius;
	}
	
	public List<String> getPreserveBlocks() {
		return this.preserveBlocks;
	}
	
	public boolean getStatus() {
		return this.status;
	}
}
