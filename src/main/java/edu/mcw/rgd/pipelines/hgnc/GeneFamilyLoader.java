package edu.mcw.rgd.pipelines.hgnc;

import edu.mcw.rgd.datamodel.HgncFamily;
import edu.mcw.rgd.process.FileDownloader;
import edu.mcw.rgd.process.Utils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.util.*;

public class GeneFamilyLoader {

    Logger logger = LogManager.getLogger("gene_families");
    private String version;
    private String familyFile;
    private String geneHasFamilyFile;

    private Dao dao = new Dao();

    public void run() throws Exception {

        long time0 = System.currentTimeMillis();
        logger.info(getVersion());
        logger.info("   " + dao.getConnectionInfo());

        loadFamilies();
        loadGeneFamilies();

        logger.info("=== OK  time elapsed " + Utils.formatElapsedTime(time0, System.currentTimeMillis()));
        logger.info("");
    }

    void loadFamilies() throws Exception {

        FileDownloader fd = new FileDownloader();
        fd.setExternalFile(getFamilyFile());
        fd.setLocalFile("data/family.csv");
        fd.setPrependDateStamp(true);
        fd.setUseCompression(true);
        String localFile = fd.downloadNew();

        // load all existing families from db into a map keyed by family_id
        Map<Integer, HgncFamily> familyMap = dao.getAllFamiliesAsMap();

        // parse CSV and process each row
        int inserted = 0;
        int updated = 0;
        int upToDate = 0;
        Set<Integer> incomingIds = new HashSet<>();

        BufferedReader reader = Utils.openReader(localFile);
        String line = reader.readLine(); // skip header line
        while ((line = reader.readLine()) != null) {

            List<String> cols = parseCsvLine(line);
            if (cols.size() < 10) {
                continue;
            }

            HgncFamily incoming = new HgncFamily();
            incoming.setFamilyId(Integer.parseInt(cols.get(0)));
            incoming.setAbbreviation(nullIfEmpty(cols.get(1)));
            incoming.setName(nullIfEmpty(cols.get(2)));
            incoming.setExternalNote(nullIfEmpty(cols.get(3)));
            incoming.setPubmedIds(nullIfEmpty(cols.get(4)));
            incoming.setDescComment(nullIfEmpty(cols.get(5)));
            incoming.setDescLabel(nullIfEmpty(cols.get(6)));
            incoming.setDescSource(nullIfEmpty(cols.get(7)));
            incoming.setDescGo(nullIfEmpty(cols.get(8)));
            incoming.setTypicalGene(nullIfEmpty(cols.get(9)));

            incomingIds.add(incoming.getFamilyId());

            HgncFamily existing = familyMap.get(incoming.getFamilyId());
            if (existing == null) {
                dao.insertFamily(incoming);
                inserted++;
            } else if (needsUpdate(existing, incoming)) {
                dao.updateFamily(existing, incoming);
                updated++;
            } else {
                upToDate++;
            }
        }
        reader.close();

        // detect obsolete families: in database but not in incoming data
        int obsolete = 0;
        for (HgncFamily f : familyMap.values()) {
            if (!incomingIds.contains(f.getFamilyId())) {
                logger.debug("OBSOLETE " + f.dump("|"));
                obsolete++;
            }
        }

        logger.info("families inserted: " + inserted);
        logger.info("families updated: " + updated);
        logger.info("families up-to-date: " + upToDate);
        logger.info("families obsolete: " + obsolete);
    }

    void loadGeneFamilies() throws Exception {

        FileDownloader fd = new FileDownloader();
        fd.setExternalFile(getGeneHasFamilyFile());
        fd.setLocalFile("data/gene_has_family.csv");
        fd.setPrependDateStamp(true);
        fd.setUseCompression(true);
        String localFile = fd.downloadNew();

        // load all existing mappings from db
        Set<String> dbMappings = dao.getAllGeneFamilyMappings();

        // parse CSV and process each row
        int inserted = 0;
        int upToDate = 0;
        Set<String> incomingMappings = new HashSet<>();

        BufferedReader reader = Utils.openReader(localFile);
        String line = reader.readLine(); // skip header line
        while ((line = reader.readLine()) != null) {

            List<String> cols = parseCsvLine(line);
            if (cols.size() < 2) {
                continue;
            }

            String hgncId = "HGNC:" + cols.get(0);
            int familyId = Integer.parseInt(cols.get(1));
            String key = hgncId + "|" + familyId;

            incomingMappings.add(key);

            if (!dbMappings.contains(key)) {
                dao.insertGeneFamilyMapping(hgncId, familyId);
                inserted++;
            } else {
                upToDate++;
            }
        }
        reader.close();

        // detect and delete obsolete mappings
        int deleted = 0;
        for (String dbKey : dbMappings) {
            if (!incomingMappings.contains(dbKey)) {
                String[] parts = dbKey.split("\\|");
                dao.deleteGeneFamilyMapping(parts[0], Integer.parseInt(parts[1]));
                deleted++;
            }
        }

        logger.info("gene-family mappings inserted: " + inserted);
        logger.info("gene-family mappings deleted: " + deleted);
        logger.info("gene-family mappings up-to-date: " + upToDate);
    }

    boolean needsUpdate(HgncFamily db, HgncFamily incoming) {
        return !Utils.stringsAreEqual(db.getAbbreviation(), incoming.getAbbreviation())
            || !Utils.stringsAreEqual(db.getName(), incoming.getName())
            || !Utils.stringsAreEqual(db.getExternalNote(), incoming.getExternalNote())
            || !Utils.stringsAreEqual(db.getPubmedIds(), incoming.getPubmedIds())
            || !Utils.stringsAreEqual(db.getDescComment(), incoming.getDescComment())
            || !Utils.stringsAreEqual(db.getDescLabel(), incoming.getDescLabel())
            || !Utils.stringsAreEqual(db.getDescSource(), incoming.getDescSource())
            || !Utils.stringsAreEqual(db.getDescGo(), incoming.getDescGo())
            || !Utils.stringsAreEqual(db.getTypicalGene(), incoming.getTypicalGene());
    }

    /**
     * convert empty strings and "NULL" to null
     */
    String nullIfEmpty(String s) {
        if (s == null || s.isEmpty() || s.equals("NULL")) {
            return null;
        }
        return s;
    }

    /**
     * parse a CSV line handling quoted fields that may contain commas
     */
    List<String> parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        sb.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    sb.append(c);
                }
            } else {
                if (c == '"') {
                    inQuotes = true;
                } else if (c == ',') {
                    fields.add(sb.toString());
                    sb.setLength(0);
                } else {
                    sb.append(c);
                }
            }
        }
        fields.add(sb.toString());
        return fields;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getFamilyFile() {
        return familyFile;
    }

    public void setFamilyFile(String familyFile) {
        this.familyFile = familyFile;
    }

    public String getGeneHasFamilyFile() {
        return geneHasFamilyFile;
    }

    public void setGeneHasFamilyFile(String geneHasFamilyFile) {
        this.geneHasFamilyFile = geneHasFamilyFile;
    }
}
