#!/usr/bin/env bash
. /etc/profile

APPNAME="hgnc-pipeline"
APPDIR=/home/rgddata/pipelines/$APPNAME
SERVER=`hostname -s | tr '[a-z]' '[A-Z]'`

EMAILLIST=mtutaj@mcw.edu
if [ "$SERVER" == "REED" ]; then
  EMAILLIST="mtutaj@mcw.edu jrsmith@mcw.edu"
fi
cd $APPDIR

$APPDIR/_run.sh --processGeneFamilies > $APPDIR/geneFamilies.log

mailx -s "[$SERVER] HGNC Gene Families OK!" $EMAILLIST < $APPDIR/logs/gene_families_summary.log
