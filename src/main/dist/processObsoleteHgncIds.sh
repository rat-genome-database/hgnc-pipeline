# process obsolete HGNC ids
. /etc/profile

APPNAME=HgncPipeline
APPDIR=/home/rgddata/pipelines/$APPNAME
SERVER=`hostname -s | tr '[a-z]' '[A-Z]'`

EMAILLIST=mtutaj@mcw.edu
if [ "$SERVER" == "REED" ]; then
  EMAILLIST=mtutaj@mcw.edu
fi

cd $APPDIR

$APPDIR/_run.sh --processObsoleteHgncIds

mailx -s "[$SERVER] Obsolete Hgnc Ids OK!" $EMAILLIST < $APPDIR/logs/obsolete_hgnc_ids_summary.log

