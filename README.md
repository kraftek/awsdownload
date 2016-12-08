#Purpose
This is a simple downloader for either Sentinel-2 or Landsat-8.
Sentinel-2 products can be downloaded from either Amazon Web Services (AWS) or ESA's SciHub.
Landsat-8 products are downloaded from Amazon Web Services.
The initial reason of creating this downloader: AWS stores the elements of an S2 product (granules, metadata files) in a way
different from the official SAFE format. Therefore, if you want to use it with tools like Sentinel-2 Toolbox 
(homepage: https://sentinel.esa.int/web/sentinel/toolboxes/sentinel-2) , you have to download it in a suitable format.

#Features
1. Can perform filter on tiles (i.e. download only tiles of interest from a product to save space) either by supplying
a tile list on the command line, or by putting tiles in a dedicated file which is supplied as argument.
2. Can download products / tiles from an area of interest (given a closed linear ring - sequence of <lon lat> pairs),
supplied either as a list of comma-separated pairs of coordinates as argument, or in a file given as argument.
3. Can download products / tiles given a list of products.
4. Can download only products / tiles whose cloud coverage is below a given threshold.
5. Can download tiles by only giving the tile list.

Filtering on products is mutually exclusive with filtering on an area of interest. The AOI is used to query SciHub for products intersecting it, optionally by supplying the start and end of sensing times. If the AOI is a polygon which has more than 200 points, the extent (bounding box) of this polygon will be used instead.

#Binaries
The latest binaries can be found at https://github.com/kraftek/awsdownload/releases/.
Starting with version 1.3-beta, there is also a GUI mode of the downloader.
The script files "launch-gui.bat"/"launch-gui.sh" should be run in order to launch the graphical interface.
For the non-gui mode, the execution is done like in the previous versions.

#How to use it with Git and Maven:
1. Locally clone the repository
    git clone https://github.com/kraftek/awsdownload.git
2. Build and package the project:
    mvn clean package
3. Assemble the jar and dependencies:
    mvn assembly:assembly
3. The folder "lib" and the generated jar should be placed on the same folder. Then:
    java -jar ProductDownload.jar
   for a list of supported command line arguments.

Note: The project requires Java 8 to be installed.

Example 1 - Download a Sentinel-2 tile (actually products with only that tile) from AWS, but querying SciHub

    java -jar ProductDownload --sensor S2 --out D:\Products --tiles 34TFQ --startdate 2016-07-12 --relative.orbit 93 --store AWS --cloudpercentage 50 --limit 10 --user <scihub_user> --password <scihub_password>

Example 2 - Download a Sentinel-2 (actually products with only that tile) from AWS without querying SciHub

    java -jar ProductDownload --sensor S2 --aws --out D:\Products --tiles 34TFQ --startdate 2016-07-12 --relative.orbit 93 --store AWS --cloudpercentage 50

Example 3 - Download a Landsat8 product from AWS

    java -jar ProductDownload --sensor L8 --out D:\Products --tiles 185029 184029 --startdate 2016-06-01 --enddate 2016-10-01 --cloudpercentage 50

Note: For Landsat8, the tiles are named PPPRRR, where PPP = path number (with leading 0s if necessary, eg. 025) and RRR = row number (with leading 0s if necessary, eg. 029)

#Configuration considerations
It may be possible that you are behind a proxy. In this case, please either pass the proxy arguments from command line or edit the download.properties file and set them accordingly.
