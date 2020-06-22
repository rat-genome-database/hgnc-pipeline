# hgnc-pipeline

##Module 'HGNC ID Nomenclature'

Updates nomenclature of human genes based on HGNC ID file from HGNC.
Also updates nomenclature for dog and pig genes based on VGNC ID file from HGNC.

##Module 'MGI Nomenclature'

Updates nomenclature of mouse genes based on file from MGI.

##Module 'Obsolete HGNC IDs'

Handles obsolete HGNC IDs for human genes. The logic is:

 * Download a file with obsoleted HGNC ids from HGNC FTP site.
 * If a processed HGNC id is withdrawn, remove it from xdbs id of genes in RGD.
 * If a processed HGNC id is replaced by another HGNC id, update it accordingly in genes xdb ids.
   However, if the to be replaced HGNC id is already present in another active gene,
   the obsolete HGNC ID is only removed from xdb ids of the obsolete gene.
