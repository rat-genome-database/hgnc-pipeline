#!/usr/bin/env bash
. /etc/profile

APPNAME="hgnc-pipeline"
APPDIR=/home/rgddata/pipelines/$APPNAME
SERVER=`hostname -s | tr '[a-z]' '[A-Z]'`

EMAILLIST=mtutaj@mcw.edu
if [ "$SERVER" == "REED" ]; then
  EMAILLIST=mtutaj@mcw.edu,jrsmith@mcw.edu,jdepons@mcw.edu
fi
cd $APPDIR

$APPDIR/_run.sh --processHgncIds > $APPDIR/hgncIds.log

mailx -s "[$SERVER] HgncDataPipeline OK!" $EMAILLIST < $APPDIR/logs/hgnc_ids_summary.log
