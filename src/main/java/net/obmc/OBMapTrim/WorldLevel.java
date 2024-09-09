package net.obmc.OBMapTrim;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import io.github.ensgijs.nbt.io.BinaryNbtHelpers;
import io.github.ensgijs.nbt.io.CompressionType;
import io.github.ensgijs.nbt.tag.CompoundTag;

public class WorldLevel {

    private final int ANVIL_VERSION_ID = 0x4abd; //19133

    private String levelName;
	private File mapFolder;
	private File regionFolder;
	private int anvilVersion;
	private String mcVersion;
	private int versionId;
	private List<File> regionFileList = new ArrayList<File>();
		
	private String msg;

	public WorldLevel( File mapFolder )  {
			
		this.mapFolder = mapFolder;
		this.regionFolder = new File( mapFolder, "/region" );
	}

	// load up the world level file
	public boolean load() {

		File levelFile = new File( this.mapFolder, "level.dat" );
		if ( !levelFile.exists() ) {
			levelFile = new File( this.mapFolder, "level.dat_old" );
			if ( !levelFile.exists() ) {
				return false;
			}
		}

		// read our level dat file and get some data
		CompoundTag levelDat = null;
		try {

			levelDat = (CompoundTag)BinaryNbtHelpers.read(levelFile, CompressionType.GZIP ).getTag();

		} catch ( IOException e ) {
			OBMapTrim.outputMsg("Failed to read level.dat or level.dat_old\n", false, true, true );
			return false;
		}

		CompoundTag dataTag = levelDat.getCompoundTag( "Data" );
		CompoundTag versionTag = dataTag.getCompoundTag("Version");

		this.mcVersion = versionTag.getString( "Name" );
		this.versionId = versionTag.getInt( "Id" );
		this.anvilVersion = dataTag.getInt( "version" );
		this.levelName = dataTag.getString( "LevelName" );

		msg = "Minecraft Version: " + mcVersion + "\n" +
				"Version ID: " + this.versionId + "\n" +
				"Snapshot: " + versionTag.getBoolean( "Snapshot" ) + "\n" +
				"Level Name: " + this.levelName + "\n" +
				"Anvil Version: " + this.anvilVersion + "\n";
		OBMapTrim.outputMsg( msg, true, false, true );

		// some checks to make sure we're processing the correct region files
		if ( this.anvilVersion != ANVIL_VERSION_ID ) {
			OBMapTrim.outputMsg( "Incorrect anvil version: " + anvilVersion + "\n", true, false, true );
			return false;
		}

		getRegionFiles();
		OBMapTrim.outputMsg( "Found " + regionFileList.size() + " region files for " + this.levelName + "\n", true, false, true );

		return true;
	}

	public Object getLevelName() {
		return this.levelName;
	}

	// return our list of region files
	public List<File> getRegionFileList() {
		return this.regionFileList;
	}

	// load up all region files
	private void getRegionFiles() {

		// get list of matching files
		File[] list = regionFolder.listFiles( new FilenameFilter() {

			private final String ext = ".mca";

			@Override
			public boolean accept( File dir, String name ) {
				return name.toLowerCase().endsWith( ext );
			}
		});

		for ( File file : list ) {
			this.regionFileList.add( file );
		}
	}
}
