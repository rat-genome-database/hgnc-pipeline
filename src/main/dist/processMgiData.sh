#!/usr/bin/env bash
. /etc/profile

APPNAME="hgnc-pipeline"
APPDIR=/home/rgddata/pipelines/$APPNAME
SERVER=`hostname -s | tr '[a-z]' '[A-Z]'`

EMAILLIST=llamers@mcw.edu,mtutaj@mcw.edu
if [ "$SERVER" == "REED" ]; then
  EMAILLIST=llamers@mcw.edu,mtutaj@mcw.edu,jrsmith@mcw.edu,jdepons@mcw.edu
fi
cd $APPDIR

$APPDIR/_run.sh --processMgiData > $APPDIR/mgi_logger.log

mailx -s "[$SERVER] HgncDataPipeline for mouse OK!" $EMAILLIST < $APPDIR/logs/mgi_summary.log
