OBMapTrim
=======================

Remove chunks from a Minecraft world based on their not being any 'preserve' blocks in the chunk.<br>
<br>

Usage
-----

&nbsp;&nbsp;java -cp OBMapTrim-1.0.jar net.obmc.OBMapTrim.OBMapTrim -w <world path> \[ -r &lt;radius&gt; \] \[ -p &lt;preserve blocks&gt; \]

Where:

    -w <world path> The path to the world folder
    -r <radius> Preserve area around current chunk being checked
    -p <preserve blocks> A list of 'preserve' blocks to look for in a chunk
    -l Load chunk data from a file

You only need to provide the world folder path. The radius and preserve block list have defaults that will be used<br>
in the event you do not provide them. While running it will output what it's doing and will write a lot of detail<br>
to a log file. The log file will have the format 'obmaptrim-YYYYMMDD-HHMMSS.log' where YYYYMMDD are the year, month<br>
and day, and HHMMSS are the hour, minutes and seconds when the program was run. The -l load option is for loading<br>
chunk data from a save file instead of scanning all region files for chunk data, instead of scanning for it. If you<br>
have a big map it will save you a lot of time in the event you need to run the program multiple times.<br>

Examples:

Simplest example would be to run with the default values. This will use the default radius and default preserve blocks:<br>

	java -cp OBMapTrim-1.0.jar net.obmc.OBMapTrim.OBMapTrim -w ~/myserver/world

Specify the radius you want to preserve around a chunk by specifying it with the -r option:

	java -cp OBMapTrim-1.0.jar net.obmc.OBMapTrim.OBMapTrim -w ~/myserver/world -r 10

Tell the program the radius and what blocks to look for in the chunks:

	java -cp OBMapTrim-1.0.jar net.obmc.OBMapTrim.OBMapTrim -w ~/myserver/world -r 10
	-p "redstone_lamp,lit_redstone_lamp,sea_lantern"

Use the chunk load option to load chunk state from the file "chunkData.dat":

	java -cp OBMapTrim-1.0.jar net.obmc.OBMapTrim.OBMapTrim -w ~/myserver/world -r 10 -l

The default radius is 3 and the default block list is based on blocks we use for our city build server:

	sponge,powered_rail,white_wool,smooth_stone_slab,anvil,redstone_lamp,lit_redstone_lamp,
	sea_lantern,beacon,redstone_block,emerald_block,barrier,anvil

Here is an image of our build server before and after running the trim with a radius of 10. This was<br>
a 186 region files, 160,000 chunks, 1.2GB map. After the clear down of the unnecessary chunks it was<br>
reduced to 116 region files, 64,293 chunks deleted and the size now 515MB.<br>

Before:<br>
<img src="https://ob-mc.net/repo/PreTrim.png" width="900" height="500">

After:<br>
<img src="https://ob-mc.net/repo/PostTrim.png" width="900" height="500">

How does it work?
-----

The program will make three passes of the world region files in order to remove chunks. The first is to<br>
get a count of chunks in each file and make an internal hash of the chunks and a unique identifier for<br>
every chunk. The second pass processes the blocks of each chunk and looks for any of the preserve blocks.<br>
If there are any, then the chunk in the hash is marked for keeping and all of the chunks in the specified<br>
radius around it will also be marked for keeping, if not already marked. At this point the chunkData.dat<br>
is created from the hashmap, which can be loaded with the -l option and therefore skipping the first two<br>
passes. Very handy if you want to perform multiple runs of the program to get the map just right. The third<br>
and final pass will remove the chunk if not marked for keeping based on the hash status. If there are no<br>
chunks to be retained in the region file, then the file is removed along with any corresponding poi end entity<br>
region files, otherwise the region file is saved to update it with any changes.<br>

<span style="color:red">IMPORTANT</span>: **Make sure you back up your world before running this program!!**<br>
If any errors are encountered processing region files, then the program will terminate. This could leave your<br>
world in an inconsistent state and corrupt. You need to maintain a backup of your world!!<br>

All of this processing is recorded in the log file.<br>

Compiled for 1.21 and Java 21.
