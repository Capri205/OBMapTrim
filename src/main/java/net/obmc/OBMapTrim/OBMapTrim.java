package net.obmc.OBMapTrim;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.ensgijs.nbt.mca.McaRegionFile;
import io.github.ensgijs.nbt.mca.TerrainChunk;
import io.github.ensgijs.nbt.mca.TerrainSection;
import io.github.ensgijs.nbt.mca.io.McaFileHelpers;
import io.github.ensgijs.nbt.mca.util.ChunkIterator;
import io.github.ensgijs.nbt.mca.util.PalettizedCuboid;
import io.github.ensgijs.nbt.mca.util.SectionIterator;
import io.github.ensgijs.nbt.tag.CompoundTag;

public class OBMapTrim {

	private static Options opt = new Options();
	private static List<String> preserveBlocks = null;
	private static ConcurrentHashMap<Long, Boolean> chunkData = new ConcurrentHashMap<>();
	private static final AtomicInteger regionFileIdx = new AtomicInteger( 0 );
	private static final AtomicInteger scanChunkProgress = new AtomicInteger( 0 );
	private static final AtomicInteger totalChunksRemoved = new AtomicInteger( 0 );
	private static final AtomicInteger totalFilesDeleted = new AtomicInteger( 0 );
	private static final AtomicInteger totalPoiFilesDeleted = new AtomicInteger( 0 );
	private static final AtomicInteger totalEntityFilesDeleted = new AtomicInteger( 0 );
	private static final AtomicInteger chunksProcessed = new AtomicInteger( 0 );

	private static final int THREAD_COUNT = 4;

	private static String msg = null;
	private static BufferedWriter logFile = null;
	private static String chunkDataFile = "chunkData.dat";
	private static final Object lock = new Object();

	public static void main( String[] args ) {
		
		// create a log file
		DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
        LocalDateTime now = LocalDateTime.now();
        String timestamp = dtf.format(now);
        String logFileName = "obmaptrim-" + timestamp + ".log";
        try {
			logFile = new BufferedWriter( new FileWriter( logFileName, false ) );
		} catch ( IOException e ) {
			outputMsg( "Failed to open a log file for this run", false, true, false );
			System.exit( 1 );
		}

        // read in our command line arguments
        opt.ReadArgs(args);
		if ( opt.getStatus() == false || opt.getMapPath() == null ) {
			printUsage();
			System.exit( 1 );
		}
		File mapFolder = new File( opt.getMapPath() );
		if( !mapFolder.exists() ) {
			msg = String.format( "Map folder does not exist: %s\n", mapFolder.getAbsolutePath() );
			outputMsg( msg , false, true, true );
			System.exit( 1 );
			return;
		}
		if ( opt.getRadius() < 0 ) {
			outputMsg( "Radius must be a positive number\n", false, true, true );
			System.exit( 1 );
		}
		if ( opt.getPreserveBlocks().size() == 0 ) {
			outputMsg( "No preserve blocks defined\n", false, true, true );
			System.exit( 1 );
		}
		if ( opt.getStatus() == false ) {
			System.exit( 1 );
		}

		// load up world information an dget a list of world region files
		WorldLevel worldLevel = new WorldLevel( mapFolder );
		if ( !worldLevel.load() ) {
			outputMsg( "Failed to load world data", false, true, true );
			System.exit( 1 );
		}
		List<File> regionFiles = worldLevel.getRegionFileList();
		if ( regionFiles.size() == 0 ) {
			System.exit( 0 );
		}

		preserveBlocks = opt.getPreserveBlocks();
		
		// load our chunk data hashmap
		if ( opt.getLoadChunkMap() == true ) {

			outputMsg( "Loading chunk map from file...\n", true, false, true );
			
			File dataFile = new File( chunkDataFile );
			if ( !dataFile.exists() ) {
				outputMsg( "Chunk data file for retry not found", false, true, true );
				System.exit( 1 );
			}
			try (FileInputStream fis = new FileInputStream( dataFile );
				ObjectInputStream ois = new ObjectInputStream(fis)) {
				chunkData = (ConcurrentHashMap<Long, Boolean>) ois.readObject();
			} catch (Exception e) {
				    e.printStackTrace();
				    System.exit( 1 );
			}
		}
		


		// first pass gathers chunk data and assume we are not keeping any chunks
		// unless we are doing a reload in which case we can skip
		if ( opt.getLoadChunkMap() == false ) {

			outputMsg( "Initializing chunk data...\n", true, false, true );

			ExecutorService executor = Executors.newFixedThreadPool( THREAD_COUNT );
			
			for ( File regionFile : regionFiles ) {

				executor.submit( () -> {

					// get the region file loaded
					McaRegionFile mcaRegionFile = null;
					try {

						mcaRegionFile = McaFileHelpers.readAuto( regionFile );

					} catch ( IOException e ) {

						outputMsg( "Failed to load region file " + regionFile.getName() + "\n", false, true, true );
						executor.shutdown();
						try {
							executor.awaitTermination( 1, TimeUnit.HOURS );
						} catch ( InterruptedException e1 ) {
							e1.printStackTrace();
						}
						System.exit( 1 );
					}
					outputMsg( regionFileIdx.get() + ": " + regionFile.getName() + " contains " + mcaRegionFile.count() + " chunk" + ( mcaRegionFile.count() != 1 ? "s" : "" ) + "\n", true, false, true );

					// pass over all chunks in region file, generate a unique key for the chunk
					// and record the key in our chunk map marking it as false (for removal)
					ChunkIterator<?> cit = mcaRegionFile.iterator();
					while ( cit.hasNext() ) {

						TerrainChunk chunk = (TerrainChunk)cit.next();
						if ( chunk == null ) {
							continue;
						}

						long chunkKey = generateHash( chunk.getChunkX(), chunk.getChunkZ() );
						chunkData.put( chunkKey, false );
					}
					regionFileIdx.incrementAndGet();
				});
			}
			executor.shutdown();
			try {
				executor.awaitTermination( 1,  TimeUnit.HOURS );
			} catch ( InterruptedException e ) {
				e.printStackTrace();
				System.exit( 1 );
			}
		}
		outputMsg( "Found " + chunkData.keySet().size() + " chunk" + ( chunkData.keySet().size() != 1 ? "s" : "" ) + " to process\n", true, false, true );
		
		// second pass look for preserve blocks in chunks and mark chunk for keeping in our map if we find any
		// or load the chunk map from the chunk map file
		if ( opt.getLoadChunkMap() == true ) {

			outputMsg( "Loading chunk map from file...\n", true, false, true );
			
			File dataFile = new File( chunkDataFile );
			if ( !dataFile.exists() ) {
				outputMsg( "Chunk data file for retry not found", false, true, true );
				System.exit( 1 );
			}
			try (FileInputStream fis = new FileInputStream( dataFile );
				ObjectInputStream ois = new ObjectInputStream(fis)) {
				chunkData = (ConcurrentHashMap<Long, Boolean>) ois.readObject();
			} catch (Exception e) {
				    e.printStackTrace();
				    System.exit( 1 );
			}

		} else {

			outputMsg( "Looking for preserve blocks. This may take a while...\n", true, false, true );

			ProgressReporter scanReporter = new ProgressReporter( "Scanning", chunkData.size() );

			ExecutorService scanExecutor = Executors.newFixedThreadPool( THREAD_COUNT );

			for ( File regionFile : regionFiles ) {

				scanExecutor.submit( () -> {
					McaRegionFile mcaRegionFile = null;
					try {

						mcaRegionFile = McaFileHelpers.readAuto( regionFile );

					} catch ( IOException e ) {

						outputMsg( "Failed to load region file " + regionFile.getName() + "\n", false, true, true );
						System.exit( 1 );
					}

					ChunkIterator<?> cit = mcaRegionFile.iterator();
					while ( cit.hasNext() ) {

						// move to next chunk if null or already marked as a keeper
						TerrainChunk chunk = (TerrainChunk)cit.next();
						if ( chunk == null ) {
							continue;
						}

						boolean keepChunk = false;

						AtomicInteger preserveBlockCheck = new AtomicInteger( 0 );

						// pass over sections in the chunk getting the block data for each and scan for preserve blocks
						SectionIterator<TerrainSection> sit = chunk.iterator();
						while ( sit.hasNext() ) {

							TerrainSection section = sit.next();
							PalettizedCuboid<CompoundTag> blockStates = section.getBlockStates();
							if ( blockStates == null ) {
								continue;
							}

							// we use a decrementing count to determine if we encountered any preserve blocks
							preserveBlockCheck.set( blockStates.size() );
							blockStates.stream().takeWhile( block -> !preserveBlocks.contains( block.getString( "Name" ).toLowerCase() ) ).forEach( block -> {
								preserveBlockCheck.decrementAndGet();
							});

							// non-zero count means we exited the block check early due to encountering a preserve block
							// also stop checking any further chunk sections/blocks as we don't need to
							if ( preserveBlockCheck.get() != 0 ) {
								keepChunk = true;
								break;
							}
						}

						scanChunkProgress.incrementAndGet();

						// process the chunk as a keeper by finding nearby chunks and marking as keepers too 
						if ( keepChunk ) {

							chunkData.replace( generateHash( chunk.getChunkX(), chunk.getChunkZ() ), true );
							outputMsg( "Keep chunk " + chunk.getChunkX() + "," + chunk.getChunkZ() + " (" + regionFile.getName() + ")\n", false, false, true );

							// get chunks the radius around this chunk and mark them all as keepers if not already marked
							Map<Long, String> nearbyChunks = getNearbyChunks( chunk.getChunkX(), chunk.getChunkZ(), opt.getRadius() );
							for ( Map.Entry<Long, String> chunkEntry : nearbyChunks.entrySet() ) {

								Long chunkKey = chunkEntry.getKey();
								int chunkX = Integer.parseInt( chunkEntry.getValue().split( "," )[0] );
								int chunkZ = Integer.parseInt( chunkEntry.getValue().split( "," )[1] );

								if ( chunkData.containsKey( chunkKey ) && chunkData.get( chunkKey ) == false ) {
									chunkData.replace( chunkKey, true );
									outputMsg( "Keep nearby chunk " + chunkX + "," + chunkZ + " (r." + ( chunkX >> 5 ) + "." + ( chunkZ >> 5 ) + ".mca)\n", false, false, true );
								}
							}
						} else {
							outputMsg( "Remove chunk " + chunk.getChunkX() + "," + chunk.getChunkZ() + " (" + regionFile.getName() + ")\n", false, false, true );
						}
					}

					scanReporter.updateSet( scanChunkProgress.get() );
				});
			}
			scanReporter.close();
			scanExecutor.shutdown();
			try {
				scanExecutor.awaitTermination( 1,  TimeUnit.HOURS );
			} catch ( InterruptedException e ) {
				e.printStackTrace();
				System.exit( 1 );
			}
		}

		int keepCount = (int) chunkData.values().stream().filter( value -> value ).count();
		int deleteCount = chunkData.keySet().size() - keepCount;
		
		String msg = "Total chunks: " + chunkData.keySet().size() + "\n" +
				"Chunks to retain: " + keepCount + "\n" +
				"Chunks to delete: " + deleteCount + "\n" +
				"\n";
		outputMsg( msg, true, false, true );
		
        
        // create a map file for faster loading on a retry if we're not already loading the file
		File dataFile = new File( chunkDataFile );
		if ( opt.getLoadChunkMap() == false ) {
			try ( FileOutputStream fos = new FileOutputStream( dataFile );
					ObjectOutputStream oos = new ObjectOutputStream( fos ) ) {
				oos.writeObject( chunkData );
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		
		Scanner scanner = new Scanner( System.in );
		System.out.print("Proceed to trim world map? Y/N: ");
		String input = scanner.nextLine();
		scanner.close();
		if ( input.equalsIgnoreCase( "Y" ) || input.equalsIgnoreCase( "YES" ) ) {
			
			outputMsg( "Proceeding to trim the map...\n", true, false, true );
			
		} else if ( input.equalsIgnoreCase( "N" ) || input.equalsIgnoreCase( "No" ) ) {
			
			System.exit( 0 );
		} else {
			
			System.exit( 0 );
		}

		// third pass remove the chunks marked false
		ProgressReporter trimReporter = new ProgressReporter( "Trimming", chunkData.size() );

		ExecutorService trimExecutor = Executors.newFixedThreadPool( THREAD_COUNT );

		for ( File regionFile : regionFiles ) {

			trimExecutor.submit( () -> {

				// get the region data
				McaRegionFile mcaRegionFile = null;
				try {

					mcaRegionFile = McaFileHelpers.readAuto( regionFile );

				} catch ( IOException e ) {

					outputMsg( "Failed to load region file " + regionFile.getName() + "\n", false, true, true );
					System.exit( 1 );
				}

				ChunkIterator<?> cit = mcaRegionFile.iterator();
				int chunkCount = mcaRegionFile.count();
				while ( cit.hasNext() ) {

					TerrainChunk chunk = (TerrainChunk)cit.next();
					if ( chunk == null ) {
						continue;
					}

					// remove chunk from region file if marked false
					long chunkKey = generateHash( chunk.getChunkX(), chunk.getChunkZ() );
					if ( chunkData.containsKey( chunkKey ) && chunkData.get( chunkKey ) == false ) {
						//					mcaRegionFile.removeChunk( chunk.getChunkX(), chunk.getChunkZ() );
						cit.remove();
						totalChunksRemoved.incrementAndGet();
						chunkCount--;
					}

					chunksProcessed.incrementAndGet();

				}

				// determine if all chunks for region file have been removed. If not, save the
				// region file, otherwise remove it and any associated poi and entity files
				if ( chunkCount > 0 ) {

					try {

						McaFileHelpers.write( mcaRegionFile, regionFile );
						outputMsg( "Saving region file " + regionFile.getName() + "\n", false, false, true );

					} catch ( IOException e ) {

						outputMsg( "Failed to save region file " + regionFile.getName() + "\n", false, true, true );
						StringWriter sw = new StringWriter();
						PrintWriter pw = new PrintWriter(sw);
						e.printStackTrace(pw);
						outputMsg( sw.toString(), false, true, true );
						System.exit( 1 );
					}

				} else {

					if ( !regionFile.delete() ) {
						outputMsg( "Failed to delete region file " + regionFile.getName() + "\n", false, true, true );
					}
					outputMsg( "Deleted region file " + regionFile.getName() + "\n", false, false, true );

					File poiRegionFile = new File( opt.getMapPath() + "/poi/" + regionFile.getName() );
					if ( poiRegionFile.exists() ) {
						if ( !poiRegionFile.delete() ) {
							outputMsg( "Failed to delete corresponding region poi file " + poiRegionFile.getName() + "\n", false, true, true );
						}
						totalPoiFilesDeleted.incrementAndGet();
					}
					outputMsg( "Deleted corresponding poi region file " + poiRegionFile.getName() + "\n", false, false, true );

					File entityRegionFile = new File( opt.getMapPath() + "/entities/" + regionFile.getName() );
					if ( entityRegionFile.exists() ) {
						if ( !entityRegionFile.delete() ) {
							outputMsg( "Failed to delete corresponding region entity file " + poiRegionFile.getName() + "\n", false, true, true );
						}
						totalEntityFilesDeleted.incrementAndGet();
					}
					outputMsg( "Deleted corresponding entity region file " + entityRegionFile.getName() + "\n", false, false, true );

					totalFilesDeleted.incrementAndGet();
				}

				trimReporter.updateSet( chunksProcessed.get() );
			});
		}
		trimExecutor.shutdown();
		try {
			trimExecutor.awaitTermination( 1,  TimeUnit.HOURS );
		} catch ( InterruptedException e ) {
			e.printStackTrace();
			System.exit( 1 );
		}
		trimReporter.close();
		
		msg = "\nRemoved " + totalChunksRemoved.get() + " chunk" + ( totalChunksRemoved.get() != 1 ? "s" : "" ) + "\n" +
				"Deleted " + totalFilesDeleted.get() + " region file" + ( totalFilesDeleted.get() != 1 ? "s" : "" ) + "\n" +
				"Deleted " + totalPoiFilesDeleted.get() + " poi region file" + ( totalPoiFilesDeleted.get() != 1 ? "s" : "" ) + "\n" +
				"Deleted " + totalEntityFilesDeleted.get() + " entity region file" + ( totalEntityFilesDeleted.get() != 1 ? "s" : "" ) + "\n";
		outputMsg( msg, true, false, true );
		
		msg = "OBMapTrim complete";
		outputMsg( msg, true, false, true );

		try {
			System.out.println("closing file");
			logFile.close();
		} catch ( IOException e ) {}
	}

	// find chunks in proximity to the chunk coordinates passed in
    private static Map<Long, String> getNearbyChunks(int chunkX, int chunkZ, int distance) {
    	
        Map<Long,String> nearbyChunks = new HashMap<>();
        
        for (int x = chunkX - distance; x <= chunkX + distance; x++) {
        	
            for (int z = chunkZ - distance; z <= chunkZ + distance; z++) {
            	
                nearbyChunks.put( generateHash( x, z ), x + "," + z );
            }
        }
        return nearbyChunks;
    }
    
	private static void printUsage() {
		String msg = "Minecraft Map Trim. Deletes unnecessary chunks to keep your map smaller.\n" +
			"\n" +
			"Usage:\n" +
			"\tjava -jar OBMapTrim.jar -w <world path> [-r <radius>] [-p <block list>]\n" +
			"Where:\n" +
			"\t-w <world path>\tPath to the world folder\n" +
			"\t-r <radius>\tNumber of chunks to preserve around a chunk that has a preserve block\n" +
			"\t-p <id list>\tComma separated ist of \"perserve blocks\".If a chunk contains any \"perserve block\", it will be preserved.\n" +
			"\t\tThere is a default set that will be used if none provided or failed to parse the provided list.\n";
		outputMsg( msg, true, false, false );
		
        System.exit(1);
	}
		
	private static long generateHash( int x, int z ) {
        return ( (long) x << 32 ) | ( z & 0xFFFFFFFFL );
    }

	// write out a message to stdout, stderr or log file
	public static void outputMsg( String msg, boolean stdout, boolean stderr, boolean log ) {
		
		if ( stdout ) { System.out.print( msg ); }
		if ( stderr ) { System.err.print( msg ); }
		if ( log ) {
			
			synchronized ( lock ) {
				try {
					logFile.write( msg );
					logFile.flush();
				} catch ( IOException e ) {
					e.printStackTrace();
					System.exit( 1 );
				}
			}
		}
	}
}
