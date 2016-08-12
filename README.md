#Purpose
This is a simple downloader for Sentinel-2 products from either Amazon Web Services (AWS) or ESA's SciHub.
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

#How to use it with Git and Maven:
1. Locally clone the repository
    git clone https://github.com/kraftek/awsdownload.git
2. Build and package the project:
    mvn clean package -DskipTests=true
3. Assemble the jar and dependencies:
    mvn assembly:assembly
3. The folder "lib" and the generated jar should be placed on the same folder. Then:
    java -jar S2ProductDownloader-1.0.jar
   for a list of supported command line arguments.

Note: The project requires Java 8 to be installed.

Example 1 - Download a tile (actually products with only that tile) from AWS, but querying SciHub 

    java -jar S2ProductDownloader-1.0.jar --out D:\Products --tiles 34TFQ --startdate 2016-07-12 --relative.orbit 93 --store AWS --cloudpercentage 50 --user <scihub_user> --password <scihub_password>

Example 2 - Download a tile (actually products with only that tile) from AWS without querying SciHub

    java -jar S2ProductDownloader-1.0.jar --out D:\Products --tiles 34TFQ --startdate 2016-07-12 --relative.orbit 93 --store AWS --cloudpercentage 50

#Configuration considerations
It may be possible that you are behind a proxy. In this case, please either pass the proxy arguments from command line or edit the download.properties file and set them accordingly.
