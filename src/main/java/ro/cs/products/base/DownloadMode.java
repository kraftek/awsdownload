package ro.cs.products.base;

/**
 * The download behavior enumeration
 *
 * @author Cosmin Cara
 */
public enum DownloadMode {
    /**
     * Product will be downloaded from the remote site and the corresponding local product,
     * if exists, it will be overwritten
     */
    OVERWRITE,
    /**
     * Product will be downloaded from the remote site and, if a corresponding local product exists,
     * the download will be resumed from the current length of the local product
     */
    RESUME,
    /**
     * The product will be copied from a local (or shared) folder into the output folder.
     * No remote download will be performed.
     * This works only in conjunction with the --input command line parameter.
     */
    COPY,
    /**
     * Only a symlink to the product file system location, into the output folder, will be created.
     * No remote download will be performed.
     * This works only in conjunction with the --input command line parameter.
     */
    SYMLINK
}
