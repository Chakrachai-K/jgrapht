/*
 * (C) Copyright 2015-2017, by Wil Selwood and Contributors.
 *
 * JGraphT : a free Java graph-theory library
 *
 * This program and the accompanying materials are dual-licensed under
 * either
 *
 * (a) the terms of the GNU Lesser General Public License version 2.1
 * as published by the Free Software Foundation, or (at your option) any
 * later version.
 *
 * or (per the licensee's choosing)
 *
 * (b) the terms of the Eclipse Public License v1.0 as published by
 * the Eclipse Foundation.
 */
package org.jgrapht.io;

import java.io.*;
import java.util.*;

import org.jgrapht.*;
import org.jgrapht.graph.*;
import org.jgrapht.io.DOTExporter;
import org.jgrapht.io.DOTImporter;
import org.jgrapht.io.ExportException;
import org.jgrapht.io.ImportException;
import org.jgrapht.io.IntegerComponentNameProvider;

import junit.framework.*;

public class DOTImporterTest
    extends TestCase
{

    private static class GraphWithID
        extends AbstractBaseGraph<String, DefaultEdge>
        implements UndirectedGraph<String, DefaultEdge>
    {

        private static final long serialVersionUID = 1L;

        String id = null;

        protected GraphWithID()
        {
            super(new ClassBasedEdgeFactory<>(DefaultEdge.class), false, false);
        }

    }

    private static DOTImporter<String, DefaultEdge> idGraphImporter =
        new DOTImporter<>(null, null, null, (component, attributes) -> {
            if (component instanceof GraphWithID) {
                ((GraphWithID) component).id = attributes.getOrDefault("ID", "G");
            }
        });

    public void testImportID()
        throws ImportException
    {
        String id = "MyGraph";

        String input = "strict graph " + id + " {\n}\n";

        GraphWithID expected = new GraphWithID();
        expected.id = id;

        GraphWithID result = new GraphWithID();
        assertNull(result.id);

        idGraphImporter.importGraph(result, new StringReader(input));
        assertEquals(expected.toString(), result.toString());
        assertEquals(expected.id, id);

    }

    public void testImportWrongID()
        throws ImportException
    {
        String invalidID = "2test";
        String input = "graph " + invalidID + " {\n}\n";

        GraphWithID result = new GraphWithID();

        try {
            idGraphImporter.importGraph(result, new StringReader(input));
            fail("Should not get here");
        } catch (ImportException e) {
            assertEquals(
                e.getMessage(), "ID in the graph is not formatted correctly: '" + invalidID + "'");
        }
    }

    public void testInvalidHeader()
        throws ImportException
    {
        // testing all cases of missing keywords or wrong order
        for (String invalidInput : new String[] { " {}", "strict {}", "id {}", "strict id {}",
            "id strict {}", "id strict graph {}", "graph strict id {}" })
        {

            GraphWithID result = new GraphWithID();

            try {
                idGraphImporter.importGraph(result, new StringReader(invalidInput));
                fail("Correctly loaded incorrect graph: " + invalidInput);
            } catch (ImportException e) {
                // this is the expected exception
            } catch (Exception e) {
                fail("Expected ImportException but found " + e.getClass().getSimpleName());
            }
        }
    }

    public void testImportOnlyGraphKeyword()
        throws ImportException
    {
        String input = "graph {\n}\n";
        GraphWithID result = new GraphWithID();
        idGraphImporter.importGraph(result, new StringReader(input));
        assertNull(result.id);
    }

    public void testImportNoID()
        throws ImportException
    {
        String input = "strict graph {\n}\n";
        GraphWithID result = new GraphWithID();
        idGraphImporter.importGraph(result, new StringReader(input));
        assertNull(result.id);
    }

    public void testUndirectedWithLabels()
        throws ImportException
    {
        String input = "graph G {\n" + "  1 [ \"label\"=\"abc123\" ];\n"
            + "  2 [ label=\"fred\" ];\n" + "  1 -- 2;\n" + "}";

        Multigraph<String, DefaultEdge> expected = new Multigraph<>(DefaultEdge.class);
        expected.addVertex("1");
        expected.addVertex("2");
        expected.addEdge("1", "2");

        DOTImporter<String, DefaultEdge> importer = buildImporter();

        Multigraph<String, DefaultEdge> result = new Multigraph<>(DefaultEdge.class);
        importer.importGraph(result, new StringReader(input));

        assertEquals(expected.toString(), result.toString());

        assertEquals(2, result.vertexSet().size());
        assertEquals(1, result.edgeSet().size());

    }

    public void testDirectedNoLabels()
        throws ImportException
    {
        String input =
            "digraph graphname {\r\n" + "     a -> b -> c;\r\n" + "     b -> d;\r\n" + " }";

        DirectedMultigraph<String, DefaultEdge> expected =
            new DirectedMultigraph<>(DefaultEdge.class);
        expected.addVertex("a");
        expected.addVertex("b");
        expected.addVertex("c");
        expected.addVertex("d");
        expected.addEdge("a", "b");
        expected.addEdge("b", "c");
        expected.addEdge("b", "d");

        DOTImporter<String, DefaultEdge> importer = buildImporter();

        DirectedMultigraph<String, DefaultEdge> result =
            new DirectedMultigraph<>(DefaultEdge.class);
        importer.importGraph(result, new StringReader(input));

        assertEquals(expected.toString(), result.toString());

        assertEquals(4, result.vertexSet().size());
        assertEquals(3, result.edgeSet().size());

    }

    public void testDirectedSameLabels()
        throws ImportException
    {
        String input =
            "digraph sample {\n" + "  a -> b;" + "  b -> c;\n" + "  a [ label=\"Test\"];\n"
                + "  b [ label=\"Test\"];\n" + "  c [ label=\"Test\"];\n" + "}";

        DirectedMultigraph<String, DefaultEdge> expected =
            new DirectedMultigraph<>(DefaultEdge.class);
        expected.addVertex("a");
        expected.addVertex("b");
        expected.addVertex("c");
        expected.addEdge("a", "b");
        expected.addEdge("b", "c");

        DOTImporter<String,
            DefaultEdge> importer = new DOTImporter<>(
                (label, attributes) -> label, (from, to, label, attributes) -> new DefaultEdge(),
                (vertex, attributes) -> {
                });

        DirectedMultigraph<String, DefaultEdge> result =
            new DirectedMultigraph<>(DefaultEdge.class);
        importer.importGraph(result, new StringReader(input));

        assertEquals(expected.toString(), result.toString());
    }

    public void testMultiLinksUndirected()
        throws ImportException
    {
        String input = "graph G {\n" + "  1 [ label=\"bob\" ];\n" + "  2 [ label=\"fred\" ];\n"
        // the extra label will be ignored but not cause any problems.
            + "  1 -- 2 [ label=\"friend\"];\n" + "  1 -- 2;\n" + "}";

        Multigraph<String, DefaultEdge> expected = new Multigraph<>(DefaultEdge.class);
        expected.addVertex("1");
        expected.addVertex("2");
        expected.addEdge("1", "2", new DefaultEdge());
        expected.addEdge("1", "2", new DefaultEdge());

        DOTImporter<String, DefaultEdge> importer = buildImporter();

        Multigraph<String, DefaultEdge> result = new Multigraph<>(DefaultEdge.class);
        importer.importGraph(result, new StringReader(input));

        assertEquals(expected.toString(), result.toString());

        assertEquals(2, result.vertexSet().size());
        assertEquals(2, result.edgeSet().size());
    }

    public void testExportImportLoop()
        throws ImportException, ExportException, UnsupportedEncodingException
    {
        DirectedMultigraph<String, DefaultEdge> start = new DirectedMultigraph<>(DefaultEdge.class);
        start.addVertex("a");
        start.addVertex("b");
        start.addVertex("c");
        start.addVertex("d");
        start.addEdge("a", "b");
        start.addEdge("b", "c");
        start.addEdge("b", "d");

        DOTExporter<String, DefaultEdge> exporter = new DOTExporter<>(
            vertex -> vertex, null, new IntegerComponentNameProvider<DefaultEdge>());

        DOTImporter<String, DefaultEdge> importer = buildImporter();

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        exporter.exportGraph(start, os);
        String output = new String(os.toByteArray(), "UTF-8");

        DirectedMultigraph<String, DefaultEdge> result =
            new DirectedMultigraph<>(DefaultEdge.class);

        importer.importGraph(result, new StringReader(output));

        assertEquals(start.toString(), result.toString());

        assertEquals(4, result.vertexSet().size());
        assertEquals(3, result.edgeSet().size());

    }

    public void testDashLabelVertex()
        throws ImportException
    {
        String input =
            "graph G {\n" + "a [label=\"------this------contains-------dashes------\"]\n" + "}";

        Multigraph<String, DefaultEdge> result = new Multigraph<>(DefaultEdge.class);

        DOTImporter<String, DefaultEdge> importer = new DOTImporter<>((label, attributes) -> {
            if (label.equals("a")) {
                assertTrue(
                    attributes.get("label").equals("------this------contains-------dashes------"));
            }
            return label;
        }, (from, to, label, attributes) -> new DefaultEdge(), (vertex, attributes) -> {
            // do nothing strings can't update.
        });

        importer.importGraph(result, new StringReader(input));

        assertEquals(1, result.vertexSet().size());
        assertTrue(result.vertexSet().contains("a"));

    }

    public void testAttributesWithNoQuotes()
        throws ImportException
    {
        String input =
            "graph G {\n" + "  1 [ label = \"bob\" \"foo\"=bar ];\n" + "  2 [ label = \"fred\" ];\n"
            // the extra label will be ignored but not cause any problems.
                + "  1 -- 2 [ label = \"friend\" \"foo\" = wibble];\n" + "}";

        Multigraph<TestVertex, TestEdge> result = new Multigraph<>(TestEdge.class);
        DOTImporter<TestVertex,
            TestEdge> importer = new DOTImporter<>(
                (label, attributes) -> new TestVertex(label, attributes),
                (from, to, label, attributes) -> new TestEdge(label, attributes));

        importer.importGraph(result, new StringReader(input));
        assertEquals("wrong size of vertexSet", 2, result.vertexSet().size());
        assertEquals("wrong size of edgeSet", 1, result.edgeSet().size());

        for (TestVertex v : result.vertexSet()) {
            if ("1".equals(v.getId())) {
                assertEquals("wrong number of attributes", 2, v.getAttributes().size());
                assertEquals("Wrong attribute values", "bar", v.getAttributes().get("foo"));
                assertEquals("Wrong attribute values", "bob", v.getAttributes().get("label"));
            } else {
                assertEquals("wrong number of attributes", 1, v.getAttributes().size());
                assertEquals("Wrong attribute values", "fred", v.getAttributes().get("label"));
            }
        }

        for (TestEdge e : result.edgeSet()) {
            assertEquals("wrong id", "friend", e.getId());
            assertEquals("wrong number of attributes", 2, e.getAttributes().size());
            assertEquals("Wrong attribute value", "wibble", e.getAttributes().get("foo"));
            assertEquals("Wrong attribute value", "friend", e.getAttributes().get("label"));
        }

    }

    public void testEmptyString()
    {
        testGarbage("", "Dot string was empty");
    }

    public void testGarbageStringEnoughLines()
    {
        String input =
            "jsfhg kjdsf hgkfds\n" + "fdsgfdsgfd\n" + "gfdgfdsgfdsg\n" + "jdhgkjfdshgsjkhl\n";

        testGarbage(input, "Invalid Header");
    }

    public void testGarbageStringInvalidFirstLine()
    {
        String input = "jsfhgkjdsfhgkfds\n" + "fdsgfdsgfd\n";

        testGarbage(input, "Invalid Header");
    }

    public void testGarbageStringNotEnoughLines()
    {
        String input = "jsfhgkjdsfhgkfds\n";

        testGarbage(input, "Invalid Header");
    }

    public void testIncompatibleGraphMulti()
    {
        String input = "strict digraph G {\n" + "a -- b\n" + "}";
        testGarbage(input, "graph defines strict but Multigraph given.");
    }

    public void testIncompatibleDirectedGraph()
    {
        String input = "digraph G {\n" + "a -- b\n" + "}";

        Multigraph<String, DefaultEdge> result = new Multigraph<>(DefaultEdge.class);

        testGarbageGraph(
            input, "input asks for directed graph but undirected graph provided.", result);
    }

    public void testIncompatibleGraph()
    {
        String input = "graph G {\n" + "a -- b\n" + "}";

        DirectedMultigraph<String, DefaultEdge> result =
            new DirectedMultigraph<>(DefaultEdge.class);

        testGarbageGraph(
            input, "input asks for undirected graph and directed graph provided.", result);
    }

    public void testInvalidAttributes()
    {
        String input =
            "graph G {\n" + "  1 [ label = \"bob\" \"foo\" ];\n" + "  2 [ label = \"fred\" ];\n"
            // the extra label will be ignored but not cause any problems.
                + "  1 -- 2 [ label = friend foo];\n" + "}";

        Multigraph<TestVertex, TestEdge> graph = new Multigraph<>(TestEdge.class);

        DOTImporter<TestVertex,
            TestEdge> importer = new DOTImporter<>(
                (label, attributes) -> new TestVertex(label, attributes),
                (from, to, label, attributes) -> new TestEdge(label, attributes));

        try {
            importer.importGraph(graph, new StringReader(input));
            fail("Should not get here");
        } catch (ImportException e) {
            assertEquals("Invalid attributes", e.getMessage());
        }
    }

    public void testUpdatingVertex()
        throws ImportException
    {
        String input = "graph G {\n" + "a -- b;\n" + "a [foo=\"bar\"];\n" + "}";
        Multigraph<TestVertex, DefaultEdge> result = new Multigraph<>(DefaultEdge.class);

        DOTImporter<TestVertex,
            DefaultEdge> importer = new DOTImporter<>(
                (label, attributes) -> new TestVertex(label, attributes),
                (from, to, label, attributes) -> new DefaultEdge(),
                (vertex, attributes) -> vertex.getAttributes().putAll(attributes));

        importer.importGraph(result, new StringReader(input));

        assertEquals("wrong size of vertexSet", 2, result.vertexSet().size());
        assertEquals("wrong size of edgeSet", 1, result.edgeSet().size());
        for (TestVertex v : result.vertexSet()) {
            if ("a".equals(v.getId())) {
                assertEquals("wrong number of attributes", 1, v.getAttributes().size());
            } else {
                assertEquals("attributes are populated", 0, v.getAttributes().size());
            }
        }

    }

    public void testParametersWithSemicolons()
        throws ImportException
    {
        String input = "graph G {\n  1 [ label=\"this label; contains a semi colon\" ];\n}\n";
        Multigraph<TestVertex, DefaultEdge> result = new Multigraph<>(DefaultEdge.class);
        DOTImporter<TestVertex,
            DefaultEdge> importer = new DOTImporter<>(
                (label, attributes) -> new TestVertex(label, attributes),
                (from, to, label, attributes) -> new DefaultEdge());

        importer.importGraph(result, new StringReader(input));
        assertEquals("wrong size of vertexSet", 1, result.vertexSet().size());
        assertEquals("wrong size of edgeSet", 0, result.edgeSet().size());
    }

    public void testLabelsWithEscapedSemicolons()
        throws ImportException
    {
        String escapedLabel = "this \\\"label; \\\"contains an escaped semi colon";
        String input = "graph G {\n node [ label=\"" + escapedLabel + "\" ];\n}\n";
        Multigraph<TestVertex, DefaultEdge> result = new Multigraph<>(DefaultEdge.class);
        DOTImporter<TestVertex, DefaultEdge> importer = new DOTImporter<>((label, attributes) -> {
            if (label.equals("node")) {
                assertEquals(attributes.get("label"), escapedLabel);
            }
            return new TestVertex(label, attributes);
        }, (from, to, label, attributes) -> new DefaultEdge());

        importer.importGraph(result, new StringReader(input));
        assertEquals("wrong size of vertexSet", 1, result.vertexSet().size());
        assertEquals("wrong size of edgeSet", 0, result.edgeSet().size());
        assertEquals(
            "wrong parsing", "node", ((TestVertex) result.vertexSet().toArray()[0]).getId());
    }

    public void testNoLineEndBetweenNodes()
        throws ImportException
    {
        String input =
            "graph G {\n  1 [ label=\"this label; contains a semi colon\" ];  2 [ label=\"wibble\" ] \n}\n";
        Multigraph<TestVertex, DefaultEdge> result = new Multigraph<>(DefaultEdge.class);
        DOTImporter<TestVertex,
            DefaultEdge> importer = new DOTImporter<>(
                (label, attributes) -> new TestVertex(label, attributes),
                (from, to, label, attributes) -> new DefaultEdge());

        importer.importGraph(result, new StringReader(input));
        assertEquals("wrong size of vertexSet", 2, result.vertexSet().size());
        assertEquals("wrong size of edgeSet", 0, result.edgeSet().size());
    }

    public void testNonConfiguredUpdate()
    {
        String input = "graph G {\n" + "a -- b // this is before the attributes for this test\n"
            + "a [foo=\"bar\"];\n" + "}";
        Multigraph<TestVertex, DefaultEdge> result = new Multigraph<>(DefaultEdge.class);
        DOTImporter<TestVertex,
            DefaultEdge> importer = new DOTImporter<>(
                (label, attributes) -> new TestVertex(label, attributes),
                (from, to, label, attributes) -> new DefaultEdge());

        try {
            importer.importGraph(result, new StringReader(input));
            fail("should not get here");
        } catch (ImportException e) {
            assertEquals(
                "exception not as expected",
                "Update required for vertex a but no vertexUpdater provided", e.getMessage());
        }

    }

    public void testWithReader()
        throws ImportException
    {
        String input = "graph G {\n" + "  1 [ \"label\"=\"abc123\" ];\n"
            + "  2 [ label=\"fred\" ];\n" + "  1 -- 2;\n" + "}";

        Multigraph<String, DefaultEdge> expected = new Multigraph<>(DefaultEdge.class);
        expected.addVertex("1");
        expected.addVertex("2");
        expected.addEdge("1", "2");

        DOTImporter<String, DefaultEdge> importer = buildImporter();

        Graph<String, DefaultEdge> result = new Multigraph<>(DefaultEdge.class);
        importer.importGraph(result, new StringReader(input));

        assertEquals(expected.toString(), result.toString());

        assertEquals(2, result.vertexSet().size());
        assertEquals(1, result.edgeSet().size());

    }

    private void testGarbage(String input, String expected)
    {
        DirectedMultigraph<String, DefaultEdge> result =
            new DirectedMultigraph<>(DefaultEdge.class);
        testGarbageGraph(input, expected, result);
    }

    private void testGarbageGraph(
        String input, String expected, AbstractBaseGraph<String, DefaultEdge> graph)
    {
        DOTImporter<String, DefaultEdge> importer = buildImporter();
        try {
            importer.importGraph(graph, new StringReader(input));
            fail("Should not get here");
        } catch (ImportException e) {
            assertEquals(expected, e.getMessage());
        }
    }

    private DOTImporter<String, DefaultEdge> buildImporter()
    {
        return new DOTImporter<>(
            (label, attributes) -> label, (from, to, label, attributes) -> new DefaultEdge());
    }

    private class TestVertex
    {
        String id;
        Map<String, String> attributes;

        public TestVertex(String id, Map<String, String> attributes)
        {
            this.id = id;
            this.attributes = attributes;
        }

        public String getId()
        {
            return id;
        }

        public Map<String, String> getAttributes()
        {
            return attributes;
        }
    }

    private class TestEdge
        extends DefaultEdge
    {
        private static final long serialVersionUID = 1L;

        String id;
        Map<String, String> attributes;

        public TestEdge(String id, Map<String, String> attributes)
        {
            super();
            this.id = id;
            this.attributes = attributes;
        }

        public String getId()
        {
            return id;
        }

        public Map<String, String> getAttributes()
        {
            return attributes;
        }
    }
}