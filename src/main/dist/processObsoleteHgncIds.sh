# process obsolete HGNC ids
. /etc/profile

APPNAME=HgncPipeline
APPDIR=/home/rgddata/pipelines/$APPNAME

EMAILLIST=mtutaj@mcw.edu
if [ "$SERVER" == "REED" ]; then
  EMAILLIST=mtutaj@mcw.edu
fi

cd $APPDIR

java -Dspring.config=$APPDIR/../properties/default_db2.xml \
    -Dlog4j.configuration=file://$APPDIR/properties/log4j.properties \
    -jar lib/$APPNAME.jar  --processObsoleteHgncIds

mailx -s "[$SERVER] Obsolete Hgnc Ids OK!" $EMAILLIST < $APPDIR/logs/obsolete_hgnc_ids_summary.log

