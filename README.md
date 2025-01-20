# hgnc-pipeline

##Module 'HGNC ID Nomenclature'

Updates nomenclature of human genes based on HGNC ID file from HGNC.
Also updates nomenclature for dog and pig genes based on VGNC ID file from HGNC.

Post-processing: clears (sets to NULL) NOMEN_SOURCE field for those genes that once were handled by the pipeline
(NOMEN_SOURCE='HGNC') but which are no longer handled by the pipeline. That allows other pipelines
(NCBI gene pipeline and Ensembl gene pipeline) to handle nomenclature for those genes.

##Module 'MGI Nomenclature'

Updates nomenclature of mouse genes based on file from MGI.

##Module 'Obsolete HGNC IDs'

Handles obsolete HGNC IDs for human genes. The logic is:

 * Download a file with obsoleted HGNC ids from HGNC FTP site.
 * Refresh OBSOLETE_HGNC_IDS table from the downloaded file.
 * If a processed HGNC id is withdrawn, remove it from xdbs id of genes in RGD.
 * If a processed HGNC id is replaced by another HGNC id, update it accordingly in genes xdb ids.
   However, if the to be replaced HGNC id is already present in another active gene,
   the obsolete HGNC ID is only removed from xdb ids of the obsolete gene.
