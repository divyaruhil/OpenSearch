/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package org.opensearch.search.query;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.document.SortedNumericDocValuesField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchNoDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.opensearch.test.OpenSearchTestCase;
import org.roaringbitmap.longlong.Roaring64NavigableMap;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * Tests for {@link Bitmap64DocValuesQuery}
 */
public class Bitmap64DocValuesQueryTests extends OpenSearchTestCase {

    /** Single value per doc */
    public void testSingleValuePerDoc() throws Exception {
        Directory dir = newDirectory();
        IndexWriter writer = new IndexWriter(dir, newIndexWriterConfig());
        
        // Add documents with single values
        addDoc(writer, 1L);
        addDoc(writer, 2L);
        addDoc(writer, 3L);
        addDoc(writer, 4L);
        
        writer.close();

        IndexReader reader = DirectoryReader.open(dir);
        IndexSearcher searcher = newSearcher(reader);

        // Bitmap with values 1 and 4
        Roaring64NavigableMap bitmap = new Roaring64NavigableMap();
        bitmap.add(1L);  // ✅ FIXED
        bitmap.add(4L);  // ✅ FIXED

        Bitmap64DocValuesQuery query = new Bitmap64DocValuesQuery("product_id", bitmap);
        TopDocs topDocs = searcher.search(query, 10);

        // Should match 2 documents (doc 0 with value 1, doc 3 with value 4)
        assertEquals(2, topDocs.totalHits.value());
        
        // Verify matched doc IDs
        Set<Integer> matchedDocs = new HashSet<>();
        for (int i = 0; i < topDocs.scoreDocs.length; i++) {
            matchedDocs.add(topDocs.scoreDocs[i].doc);
        }
        assertTrue(matchedDocs.contains(0)); // doc with value 1
        assertTrue(matchedDocs.contains(3)); // doc with value 4

        reader.close();
        dir.close();
    }

    /** Multi-value per doc */
    public void testMultiValuePerDoc() throws Exception {
        Directory dir = newDirectory();
        IndexWriter writer = new IndexWriter(dir, newIndexWriterConfig());
        
        // Doc 0: value 1
        addDoc(writer, 1L);

        // Doc 1: values 2 and 3 (multi-valued)
        Document doc = new Document();
        doc.add(new LongPoint("product_id", 2L));
        doc.add(new SortedNumericDocValuesField("product_id", 2L));
        doc.add(new LongPoint("product_id", 3L));
        doc.add(new SortedNumericDocValuesField("product_id", 3L));
        writer.addDocument(doc);

        // Doc 2: value 3
        addDoc(writer, 3L);
        
        // Doc 3: value 4
        addDoc(writer, 4L);
        
        writer.close();

        IndexReader reader = DirectoryReader.open(dir);
        IndexSearcher searcher = newSearcher(reader);

        // Bitmap with value 3
        Roaring64NavigableMap bitmap = new Roaring64NavigableMap();
        bitmap.add(3L);  // ✅ FIXED

        Bitmap64DocValuesQuery query = new Bitmap64DocValuesQuery("product_id", bitmap);
        TopDocs topDocs = searcher.search(query, 10);

        // Should match 2 documents (doc 1 has value 3, doc 2 has value 3)
        assertEquals(2, topDocs.totalHits.value());
        
        // Verify matched doc IDs
        Set<Integer> matchedDocs = new HashSet<>();
        for (int i = 0; i < topDocs.scoreDocs.length; i++) {
            matchedDocs.add(topDocs.scoreDocs[i].doc);
        }
        assertEquals(Set.of(1, 2), matchedDocs);

        reader.close();
        dir.close();
    }

    public void testEmptyBitmap() throws Exception {
        Directory dir = newDirectory();
        IndexWriter writer = new IndexWriter(dir, newIndexWriterConfig());
        
        addDoc(writer, 42L);
        writer.close();

        IndexReader reader = DirectoryReader.open(dir);
        IndexSearcher searcher = newSearcher(reader);

        Roaring64NavigableMap bitmap = new Roaring64NavigableMap();
        Query query = new Bitmap64DocValuesQuery("product_id", bitmap);
        
        TopDocs topDocs = searcher.search(query, 10);
        assertEquals(0, topDocs.totalHits.value());

        reader.close();
        dir.close();
    }

    public void testEmptyBitmapRewritesToMatchNoDocs() throws Exception {
        Roaring64NavigableMap bitmap = new Roaring64NavigableMap();
        Query query = new Bitmap64DocValuesQuery("product_id", bitmap);

        Query rewritten = query.rewrite(null);
        assertTrue(rewritten instanceof MatchNoDocsQuery);
    }

    public void testRangeOptimization() throws Exception {
        Directory dir = newDirectory();
        IndexWriter writer = new IndexWriter(dir, newIndexWriterConfig());
        
        // Add documents with values 0-99
        for (long i = 0; i < 100; i++) {
            addDoc(writer, i);
        }
        writer.close();

        IndexReader reader = DirectoryReader.open(dir);
        IndexSearcher searcher = newSearcher(reader);

        // Bitmap with values 10-20
        Roaring64NavigableMap bitmap = new Roaring64NavigableMap();
        for (long i = 10; i <= 20; i++) {
            bitmap.add(i);  // ✅ FIXED
        }
        
        Query query = new Bitmap64DocValuesQuery("product_id", bitmap);
        TopDocs topDocs = searcher.search(query, 20);
        
        assertEquals(11, topDocs.totalHits.value());

        reader.close();
        dir.close();
    }

    public void testLargeValues() throws Exception {
        Directory dir = newDirectory();
        IndexWriter writer = new IndexWriter(dir, newIndexWriterConfig());
        
        // Add documents with large long values
        long[] values = {
            Long.MAX_VALUE - 100,
            Long.MAX_VALUE - 50,
            Long.MAX_VALUE - 10,
            Long.MAX_VALUE - 1
        };
        
        for (long value : values) {
            addDoc(writer, value);
        }
        writer.close();

        IndexReader reader = DirectoryReader.open(dir);
        IndexSearcher searcher = newSearcher(reader);

        // Bitmap with large values
        Roaring64NavigableMap bitmap = new Roaring64NavigableMap();
        bitmap.add(Long.MAX_VALUE - 50);
        bitmap.add(Long.MAX_VALUE - 10);
        
        Query query = new Bitmap64DocValuesQuery("product_id", bitmap);
        TopDocs topDocs = searcher.search(query, 10);
        
        assertEquals(2, topDocs.totalHits.value());

        reader.close();
        dir.close();
    }


    public void testNullFieldThrowsException() {
        Roaring64NavigableMap bitmap = new Roaring64NavigableMap();
        bitmap.add(1L);
        
        expectThrows(IllegalArgumentException.class, () -> {
            new Bitmap64DocValuesQuery(null, bitmap);
        });
    }

    public void testNullBitmapThrowsException() {
        expectThrows(IllegalArgumentException.class, () -> {
            new Bitmap64DocValuesQuery("field", null);
        });
    }

    /**
     * Helper method to add a document with both point values and doc values
     */
    private void addDoc(IndexWriter writer, long value) throws IOException {
        Document doc = new Document();
        doc.add(new LongPoint("product_id", value));
        doc.add(new SortedNumericDocValuesField("product_id", value));
        writer.addDocument(doc);
    }
}

