// License: GPL. See LICENSE file for details.
package org.openstreetmap.josm.io;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.Location;

import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.DataSource;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.NodeData;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.OsmPrimitiveType;
import org.openstreetmap.josm.data.osm.PrimitiveData;
import org.openstreetmap.josm.data.osm.PrimitiveId;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.RelationData;
import org.openstreetmap.josm.data.osm.RelationMember;
import org.openstreetmap.josm.data.osm.SimplePrimitiveId;
import org.openstreetmap.josm.data.osm.User;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.osm.WayData;
import org.openstreetmap.josm.gui.progress.NullProgressMonitor;
import org.openstreetmap.josm.gui.progress.ProgressMonitor;
import org.openstreetmap.josm.tools.CheckParameterUtil;
import org.openstreetmap.josm.tools.DateUtils;

/**
 * Parser for the Osm Api. Read from an input stream and construct a dataset out of it.
 *
 * For each xml element, there is a dedicated method.
 * The XMLStreamReader cursor points to the start of the element, when the method is
 * entered, and it must point to the end of the same element, when it is exited.
 */
public class OsmReader {

    /**
     * Used as a temporary storage for relation members, before they
     * are resolved into pointers to real objects.
     */
    private static class RelationMemberData {
        public OsmPrimitiveType type;
        public long id;
        public String role;
    }

    /**
     * The dataset to add parsed objects to.
     */
    private DataSet ds = new DataSet();

    private XMLStreamReader parser;

    /** the map from external ids to read OsmPrimitives. External ids are
     * longs too, but in contrast to internal ids negative values are used
     * to identify primitives unknown to the OSM server
     */
    private Map<PrimitiveId, OsmPrimitive> externalIdMap = new HashMap<PrimitiveId, OsmPrimitive>();

    /**
     * Data structure for the remaining way objects
     */
    private Map<Long, Collection<Long>> ways = new HashMap<Long, Collection<Long>>();

    /**
     * Data structure for relation objects
     */
    private Map<Long, Collection<RelationMemberData>> relations = new HashMap<Long, Collection<RelationMemberData>>();

    /**
     * constructor (for private use only)
     *
     * @see #parseDataSet(InputStream, DataSet, ProgressMonitor)
     */
    private OsmReader() {
        externalIdMap = new HashMap<PrimitiveId, OsmPrimitive>();
    }

    public void setParser(XMLStreamReader parser) {
        this.parser = parser;
    }

    /**
     * Replies the parsed data set
     *
     * @return the parsed data set
     */
    public DataSet getDataSet() {
        return ds;
    }

    protected void throwException(String msg) throws XMLStreamException {
        throw new OsmParsingException(msg, parser.getLocation());
    }

    public void parse() throws XMLStreamException {
        int event = parser.getEventType();
        while (true) {
            if (event == XMLStreamConstants.START_ELEMENT) {
                if (parser.getLocalName().equals("osm") || parser.getLocalName().equals("osmChange")) {
                    parseOsm();
                } else {
                    parseUnkown();
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                return;
            }
            if (parser.hasNext()) {
                event = parser.next();
            } else {
                break;
            }
        }
        parser.close();
    }

    private void parseOsm() throws XMLStreamException {
        String v = parser.getAttributeValue(null, "version");
        if (v == null) {
            throwException(tr("Missing mandatory attribute ''{0}''.", "version"));
        }
        if (!(v.equals("0.5") || v.equals("0.6"))) {
            throwException(tr("Unsupported version: {0}", v));
        }
        String generator = parser.getAttributeValue(null, "generator");
        ds.setVersion(v);
        while (true) {
            int event = parser.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                if (parser.getLocalName().equals("bounds")) {
                    parseBounds(generator);
                } else if (parser.getLocalName().equals("node")) {
                    parseNode();
                } else if (parser.getLocalName().equals("way")) {
                    parseWay();
                } else if (parser.getLocalName().equals("relation")) {
                    parseRelation();
                } else {
                    parseUnkown();
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                return;
            }
        }
    }

    private void parseBounds(String generator) throws XMLStreamException {
        String minlon = parser.getAttributeValue(null, "minlon");
        String minlat = parser.getAttributeValue(null, "minlat");
        String maxlon = parser.getAttributeValue(null, "maxlon");
        String maxlat = parser.getAttributeValue(null, "maxlat");
        String origin = parser.getAttributeValue(null, "origin");
        if (minlon != null && maxlon != null && minlat != null && maxlat != null) {
            if (origin == null) {
                origin = generator;
            }
            Bounds bounds = new Bounds(
                    Double.parseDouble(minlat), Double.parseDouble(minlon),
                    Double.parseDouble(maxlat), Double.parseDouble(maxlon));
            if (bounds.isOutOfTheWorld()) {
                Bounds copy = new Bounds(bounds);
                bounds.normalize();
                System.out.println("Bbox " + copy + " is out of the world, normalized to " + bounds);
            }
            DataSource src = new DataSource(bounds, origin);
            ds.dataSources.add(src);
        } else {
            throwException(tr(
                    "Missing mandatory attributes on element ''bounds''. Got minlon=''{0}'',minlat=''{1}'',maxlon=''{3}'',maxlat=''{4}'', origin=''{5}''.",
                    minlon, minlat, maxlon, maxlat, origin
            ));
        }
        jumpToEnd();
    }

    private void parseNode() throws XMLStreamException {
        NodeData nd = new NodeData();
        nd.setCoor(new LatLon(Double.parseDouble(parser.getAttributeValue(null, "lat")), Double.parseDouble(parser.getAttributeValue(null, "lon"))));
        readCommon(nd);
        Node n = new Node(nd.getId(), nd.getVersion());
        n.setVisible(nd.isVisible());
        n.load(nd);
        externalIdMap.put(nd.getPrimitiveId(), n);
        while (true) {
            int event = parser.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                if (parser.getLocalName().equals("tag")) {
                    parseTag(n);
                } else {
                    parseUnkown();
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                return;
            }
        }
    }

    private void parseWay() throws XMLStreamException {
        WayData wd = new WayData();
        readCommon(wd);
        Way w = new Way(wd.getId(), wd.getVersion());
        w.setVisible(wd.isVisible());
        w.load(wd);
        externalIdMap.put(wd.getPrimitiveId(), w);

        Collection<Long> nodeIds = new ArrayList<Long>();
        while (true) {
            int event = parser.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                if (parser.getLocalName().equals("nd")) {
                    nodeIds.add(parseWayNode(w));
                } else if (parser.getLocalName().equals("tag")) {
                    parseTag(w);
                } else {
                    parseUnkown();
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                break;
            }
        }
        if (w.isDeleted() && nodeIds.size() > 0) {
            System.out.println(tr("Deleted way {0} contains nodes", w.getUniqueId()));
            nodeIds = new ArrayList<Long>();
        }
        ways.put(wd.getUniqueId(), nodeIds);
    }

    private long parseWayNode(Way w) throws XMLStreamException {
        if (parser.getAttributeValue(null, "ref") == null) {
            throwException(
                    tr("Missing mandatory attribute ''{0}'' on <nd> of way {1}.", "ref", w.getUniqueId())
            );
        }
        long id = getLong("ref");
        if (id == 0) {
            throwException(
                    tr("Illegal value of attribute ''ref'' of element <nd>. Got {0}.", id)
            );
        }
        jumpToEnd();
        return id;
    }

    private void parseRelation() throws XMLStreamException {
        RelationData rd = new RelationData();
        readCommon(rd);
        Relation r = new Relation(rd.getId(), rd.getVersion());
        r.setVisible(rd.isVisible());
        r.load(rd);
        externalIdMap.put(rd.getPrimitiveId(), r);

        Collection<RelationMemberData> members = new ArrayList<RelationMemberData>();
        while (true) {
            int event = parser.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                if (parser.getLocalName().equals("member")) {
                    members.add(parseRelationMember(r));
                } else if (parser.getLocalName().equals("tag")) {
                    parseTag(r);
                } else {
                    parseUnkown();
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                break;
            }
        }
        if (r.isDeleted() && members.size() > 0) {
            System.out.println(tr("Deleted relation {0} contains members", r.getUniqueId()));
            members = new ArrayList<RelationMemberData>();
        }
        relations.put(rd.getUniqueId(), members);
    }

    private RelationMemberData parseRelationMember(Relation r) throws XMLStreamException {
        RelationMemberData emd = new RelationMemberData();
        String value = parser.getAttributeValue(null, "ref");
        if (value == null) {
            throwException(tr("Missing attribute ''ref'' on member in relation {0}.",r.getUniqueId()));
        }
        try {
            emd.id = Long.parseLong(value);
        } catch(NumberFormatException e) {
            throwException(tr("Illegal value for attribute ''ref'' on member in relation {0}. Got {1}", Long.toString(r.getUniqueId()),value));
        }
        value = parser.getAttributeValue(null, "type");
        if (value == null) {
            throwException(tr("Missing attribute ''type'' on member {0} in relation {1}.", Long.toString(emd.id), Long.toString(r.getUniqueId())));
        }
        try {
            emd.type = OsmPrimitiveType.fromApiTypeName(value);
        } catch(IllegalArgumentException e) {
            throwException(tr("Illegal value for attribute ''type'' on member {0} in relation {1}. Got {2}.", Long.toString(emd.id), Long.toString(r.getUniqueId()), value));
        }
        value = parser.getAttributeValue(null, "role");
        emd.role = value;

        if (emd.id == 0) {
            throwException(tr("Incomplete <member> specification with ref=0"));
        }
        jumpToEnd();
        return emd;
    }

    private void parseTag(OsmPrimitive osm) throws XMLStreamException {
        String key = parser.getAttributeValue(null, "k");
        String value = parser.getAttributeValue(null, "v");
        if (key == null || value == null) {
            throwException(tr("Missing key or value attribute in tag."));
        }
        osm.put(key.intern(), value.intern());
        jumpToEnd();
    }

    /**
     * When cursor is at the start of an element, moves it to the end tag of that element.
     * Nested content is skipped.
     *
     * This is basically the same code as parseUnkown(), except for the warnings, which
     * are displayed for inner elements and not at top level.
     */
    private void jumpToEnd() throws XMLStreamException {
        while (true) {
            int event = parser.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                parseUnkown();
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                return;
            }
        }
    }

    private void parseUnkown() throws XMLStreamException {
        parseUnkown(true);
    }

    private void parseUnkown(boolean printWarning) throws XMLStreamException {
        if (printWarning) {
            System.out.println(tr("Undefined element ''{0}'' found in input stream. Skipping.", parser.getLocalName()));
        }
        while (true) {
            int event = parser.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                parseUnkown(false); /* no more warning for inner elements */
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                return;
            }
        }
    }

    private User createUser(String uid, String name) throws XMLStreamException {
        if (uid == null) {
            if (name == null)
                return null;
            return User.createLocalUser(name);
        }
        try {
            long id = Long.parseLong(uid);
            return User.createOsmUser(id, name);
        } catch(NumberFormatException e) {
            throwException(MessageFormat.format("Illegal value for attribute ''uid''. Got ''{0}''.", uid));
        }
        return null;
    }

    /**
     * Read out the common attributes and put them into current OsmPrimitive.
     */
    private void readCommon(PrimitiveData current) throws XMLStreamException {
        current.setId(getLong("id"));
        if (current.getUniqueId() == 0) {
            throwException(tr("Illegal object with ID=0."));
        }

        String time = parser.getAttributeValue(null, "timestamp");
        if (time != null && time.length() != 0) {
            current.setTimestamp(DateUtils.fromString(time));
        }

        // user attribute added in 0.4 API
        String user = parser.getAttributeValue(null, "user");
        // uid attribute added in 0.6 API
        String uid = parser.getAttributeValue(null, "uid");
        current.setUser(createUser(uid, user));

        // visible attribute added in 0.4 API
        String visible = parser.getAttributeValue(null, "visible");
        if (visible != null) {
            current.setVisible(Boolean.parseBoolean(visible));
        }

        String versionString = parser.getAttributeValue(null, "version");
        int version = 0;
        if (versionString != null) {
            try {
                version = Integer.parseInt(versionString);
            } catch(NumberFormatException e) {
                throwException(tr("Illegal value for attribute ''version'' on OSM primitive with ID {0}. Got {1}.", Long.toString(current.getUniqueId()), versionString));
            }
            if (ds.getVersion().equals("0.6")){
                if (version <= 0 && current.getUniqueId() > 0) {
                    throwException(tr("Illegal value for attribute ''version'' on OSM primitive with ID {0}. Got {1}.", Long.toString(current.getUniqueId()), versionString));
                } else if (version < 0 && current.getUniqueId() <= 0) {
                    System.out.println(tr("WARNING: Normalizing value of attribute ''version'' of element {0} to {2}, API version is ''{3}''. Got {1}.", current.getUniqueId(), version, 0, "0.6"));
                    version = 0;
                }
            } else if (ds.getVersion().equals("0.5")) {
                if (version <= 0 && current.getUniqueId() > 0) {
                    System.out.println(tr("WARNING: Normalizing value of attribute ''version'' of element {0} to {2}, API version is ''{3}''. Got {1}.", current.getUniqueId(), version, 1, "0.5"));
                    version = 1;
                } else if (version < 0 && current.getUniqueId() <= 0) {
                    System.out.println(tr("WARNING: Normalizing value of attribute ''version'' of element {0} to {2}, API version is ''{3}''. Got {1}.", current.getUniqueId(), version, 0, "0.5"));
                    version = 0;
                }
            } else {
                // should not happen. API version has been checked before
                throwException(tr("Unknown or unsupported API version. Got {0}.", ds.getVersion()));
            }
        } else {
            // version expected for OSM primitives with an id assigned by the server (id > 0), since API 0.6
            //
            if (current.getUniqueId() > 0 && ds.getVersion() != null && ds.getVersion().equals("0.6")) {
                throwException(tr("Missing attribute ''version'' on OSM primitive with ID {0}.", Long.toString(current.getUniqueId())));
            } else if (current.getUniqueId() > 0 && ds.getVersion() != null && ds.getVersion().equals("0.5")) {
                // default version in 0.5 files for existing primitives
                System.out.println(tr("WARNING: Normalizing value of attribute ''version'' of element {0} to {2}, API version is ''{3}''. Got {1}.", current.getUniqueId(), version, 1, "0.5"));
                version= 1;
            } else if (current.getUniqueId() <= 0 && ds.getVersion() != null && ds.getVersion().equals("0.5")) {
                // default version in 0.5 files for new primitives, no warning necessary. This is
                // (was) legal in API 0.5
                version= 0;
            }
        }
        current.setVersion(version);

        String action = parser.getAttributeValue(null, "action");
        if (action == null) {
            // do nothing
        } else if (action.equals("delete")) {
            current.setDeleted(true);
            current.setModified(current.isVisible());
        } else if (action.equals("modify")) {
            current.setModified(true);
        }

        String v = parser.getAttributeValue(null, "changeset");
        if (v == null) {
            current.setChangesetId(0);
        } else {
            try {
                current.setChangesetId(Integer.parseInt(v));
            } catch(NumberFormatException e) {
                if (current.getUniqueId() <= 0) {
                    // for a new primitive we just log a warning
                    System.out.println(tr("Illegal value for attribute ''changeset'' on new object {1}. Got {0}. Resetting to 0.", v, current.getUniqueId()));
                    current.setChangesetId(0);
                } else {
                    // for an existing primitive this is a problem
                    throwException(tr("Illegal value for attribute ''changeset''. Got {0}.", v));
                }
            }
            if (current.getChangesetId() <=0) {
                if (current.getUniqueId() <= 0) {
                    // for a new primitive we just log a warning
                    System.out.println(tr("Illegal value for attribute ''changeset'' on new object {1}. Got {0}. Resetting to 0.", v, current.getUniqueId()));
                    current.setChangesetId(0);
                } else {
                    // for an existing primitive this is a problem
                    throwException(tr("Illegal value for attribute ''changeset''. Got {0}.", v));
                }
            }
        }
    }

    private long getLong(String name) throws XMLStreamException {
        String value = parser.getAttributeValue(null, name);
        if (value == null) {
            throwException(tr("Missing required attribute ''{0}''.",name));
        }
        try {
            return Long.parseLong(value);
        } catch(NumberFormatException e) {
            throwException(tr("Illegal long value for attribute ''{0}''. Got ''{1}''.",name, value));
        }
        return 0; // should not happen
    }

    private static class OsmParsingException extends XMLStreamException {
        public OsmParsingException() {
            super();
        }

        public OsmParsingException(String msg) {
            super(msg);
        }

        public OsmParsingException(String msg, Location location) {
            super(msg); /* cannot use super(msg, location) because it messes with the message preventing localization */
            this.location = location;
        }

        public OsmParsingException(String msg, Location location, Throwable th) {
            super(msg, th);
            this.location = location;
        }

        public OsmParsingException(String msg, Throwable th) {
            super(msg, th);
        }

        public OsmParsingException(Throwable th) {
            super(th);
        }

        @Override
        public String getMessage() {
            String msg = super.getMessage();
            if (msg == null) {
                msg = getClass().getName();
            }
            if (getLocation() == null)
                return msg;
            msg = msg + " " + tr("(at line {0}, column {1})", getLocation().getLineNumber(), getLocation().getColumnNumber());
            return msg;
        }
    }

    /**
     * Processes the parsed nodes after parsing. Just adds them to
     * the dataset
     *
     */
    protected void processNodesAfterParsing() {
        for (OsmPrimitive primitive: externalIdMap.values()) {
            if (primitive instanceof Node) {
                this.ds.addPrimitive(primitive);
            }
        }
    }

    /**
     * Processes the ways after parsing. Rebuilds the list of nodes of each way and
     * adds the way to the dataset
     *
     * @throws IllegalDataException thrown if a data integrity problem is detected
     */
    protected void processWaysAfterParsing() throws IllegalDataException{
        for (Long externalWayId: ways.keySet()) {
            Way w = (Way)externalIdMap.get(new SimplePrimitiveId(externalWayId, OsmPrimitiveType.WAY));
            List<Node> wayNodes = new ArrayList<Node>();
            for (long id : ways.get(externalWayId)) {
                Node n = (Node)externalIdMap.get(new SimplePrimitiveId(id, OsmPrimitiveType.NODE));
                if (n == null) {
                    if (id <= 0)
                        throw new IllegalDataException (
                                tr("Way with external ID ''{0}'' includes missing node with external ID ''{1}''.",
                                        externalWayId,
                                        id));
                    // create an incomplete node if necessary
                    //
                    n = (Node)ds.getPrimitiveById(id,OsmPrimitiveType.NODE);
                    if (n == null) {
                        n = new Node(id);
                        ds.addPrimitive(n);
                    }
                }
                if (n.isDeleted()) {
                    System.out.println(tr("Deleted node {0} is part of way {1}", id, w.getId()));
                } else {
                    wayNodes.add(n);
                }
            }
            w.setNodes(wayNodes);
            if (w.hasIncompleteNodes()) {
                  System.out.println(tr("Way {0} with {1} nodes has incomplete nodes because at least one node was missing in the loaded data.",
                          externalWayId, w.getNodesCount()));
            }
            ds.addPrimitive(w);
        }
    }

    /**
     * Completes the parsed relations with its members.
     *
     * @throws IllegalDataException thrown if a data integrity problem is detected, i.e. if a
     * relation member refers to a local primitive which wasn't available in the data
     *
     */
    private void processRelationsAfterParsing() throws IllegalDataException {

        // First add all relations to make sure that when relation reference other relation, the referenced will be already in dataset
        for (Long externalRelationId : relations.keySet()) {
            Relation relation = (Relation) externalIdMap.get(
                    new SimplePrimitiveId(externalRelationId, OsmPrimitiveType.RELATION)
            );
            ds.addPrimitive(relation);
        }

        for (Long externalRelationId : relations.keySet()) {
            Relation relation = (Relation) externalIdMap.get(
                    new SimplePrimitiveId(externalRelationId, OsmPrimitiveType.RELATION)
            );
            List<RelationMember> relationMembers = new ArrayList<RelationMember>();
            for (RelationMemberData rm : relations.get(externalRelationId)) {
                OsmPrimitive primitive = null;

                // lookup the member from the map of already created primitives
                primitive = externalIdMap.get(new SimplePrimitiveId(rm.id, rm.type));

                if (primitive == null) {
                    if (rm.id <= 0)
                        // relation member refers to a primitive with a negative id which was not
                        // found in the data. This is always a data integrity problem and we abort
                        // with an exception
                        //
                        throw new IllegalDataException(
                                tr("Relation with external id ''{0}'' refers to a missing primitive with external id ''{1}''.",
                                        externalRelationId,
                                        rm.id));

                    // member refers to OSM primitive which was not present in the parsed data
                    // -> create a new incomplete primitive and add it to the dataset
                    //
                    primitive = ds.getPrimitiveById(rm.id, rm.type);
                    if (primitive == null) {
                        switch (rm.type) {
                        case NODE:
                            primitive = new Node(rm.id); break;
                        case WAY:
                            primitive = new Way(rm.id); break;
                        case RELATION:
                            primitive = new Relation(rm.id); break;
                        default: throw new AssertionError(); // can't happen
                        }

                        ds.addPrimitive(primitive);
                        externalIdMap.put(new SimplePrimitiveId(rm.id, rm.type), primitive);
                    }
                }
                if (primitive.isDeleted()) {
                    System.out.println(tr("Deleted member {0} is used by relation {1}", primitive.getId(), relation.getId()));
                } else {
                    relationMembers.add(new RelationMember(rm.role, primitive));
                }
            }
            relation.setMembers(relationMembers);
        }
    }

    /**
     * Parse the given input source and return the dataset.
     *
     * @param source the source input stream. Must not be null.
     * @param progressMonitor  the progress monitor. If null, {@see NullProgressMonitor#INSTANCE} is assumed
     *
     * @return the dataset with the parsed data
     * @throws IllegalDataException thrown if the an error was found while parsing the data from the source
     * @throws IllegalArgumentException thrown if source is null
     */
    public static DataSet parseDataSet(InputStream source, ProgressMonitor progressMonitor) throws IllegalDataException {
        if (progressMonitor == null) {
            progressMonitor = NullProgressMonitor.INSTANCE;
        }
        CheckParameterUtil.ensureParameterNotNull(source, "source");
        OsmReader reader = new OsmReader();
        try {
            progressMonitor.beginTask(tr("Prepare OSM data...", 2));
            progressMonitor.indeterminateSubTask(tr("Parsing OSM data..."));

            InputStreamReader ir = UTFInputStreamReader.create(source, "UTF-8");
            XMLStreamReader parser = XMLInputFactory.newInstance().createXMLStreamReader(ir);
            reader.setParser(parser);
            reader.parse();
            progressMonitor.worked(1);

            progressMonitor.indeterminateSubTask(tr("Preparing data set..."));
            reader.ds.beginUpdate();
            try {
                reader.processNodesAfterParsing();
                reader.processWaysAfterParsing();
                reader.processRelationsAfterParsing();
            } finally {
                reader.ds.endUpdate();
            }
            progressMonitor.worked(1);
            return reader.getDataSet();
        } catch(IllegalDataException e) {
            throw e;
        } catch(OsmParsingException e) {
            throw new IllegalDataException(e.getMessage(), e);
        } catch(XMLStreamException e) {
            String msg = e.getMessage();
            Pattern p = Pattern.compile("Message: (.+)");
            Matcher m = p.matcher(msg);
            if (m.find()) {
                msg = m.group(1);
            }
            if (e.getLocation() != null) {
                throw new IllegalDataException(tr("Line {0} column {1}: ", e.getLocation().getLineNumber(), e.getLocation().getColumnNumber()) + msg, e);
            } else {
                throw new IllegalDataException(msg, e);
            }
        } catch(Exception e) {
            throw new IllegalDataException(e);
        } finally {
            progressMonitor.finishTask();
        }
    }
}
