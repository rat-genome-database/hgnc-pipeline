#!/usr/bin/env bash
# shell script to run Hgnc pipeline
. /etc/profile

APPNAME=HgncPipeline
APPDIR=/home/rgddata/pipelines/$APPNAME
SERVER=`hostname -s | tr '[a-z]' '[A-Z]'`

EMAILLIST=hsnalabolu@mcw.edu,mtutaj@mcw.edu
if [ "$SERVER" == "REED" ]; then
  EMAILLIST=hsnalabolu@mcw.edu,mtutaj@mcw.edu,jrsmith@mcw.edu,jdepons@mcw.edu
fi
cd $APPDIR

java -Dspring.config=$APPDIR/../properties/default_db2.xml \
    -Dlog4j.configuration=file://$APPDIR/properties/log4j.properties \
    -jar lib/$APPNAME.jar --processHgncIds > $APPDIR/hgncIds.log


mailx -s "[$SERVER] HgncDataPipeline OK!" $EMAILLIST < $APPDIR/logs/hgnc_ids_summary.log
