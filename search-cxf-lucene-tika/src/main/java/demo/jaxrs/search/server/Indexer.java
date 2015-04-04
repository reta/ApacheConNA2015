/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package demo.jaxrs.search.server;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;

import org.apache.cxf.jaxrs.ext.search.tika.LuceneDocumentMetadata;
import org.apache.cxf.jaxrs.ext.search.tika.TikaLuceneContentExtractor;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.SimpleFSDirectory;
import org.apache.lucene.util.Version;

public class Indexer {
	private final Storage storage; 
	private final Directory directory = new SimpleFSDirectory(new File("index"));
	private final TikaLuceneContentExtractor extractor;
	private final Analyzer analyzer;
	
	public Indexer(final TikaLuceneContentExtractor extractor, final Storage storage, 
			final Analyzer analyzer) throws IOException {
		this.extractor = extractor;
		this.storage = storage;
		this.analyzer = analyzer;
	}
		
	public void storeAndIndex(final LuceneDocumentMetadata metadata, final byte[] content) throws IOException {
        BufferedInputStream in = null;   
        
        try {
            in = new BufferedInputStream(new ByteArrayInputStream(content));
            
            final Document document = extractor.extract(in, metadata);
            if (document != null) {                    
                final IndexWriter writer = getIndexWriter();
                
                try {                                              
                    storage.addDocument(metadata.getSource(), content);
                    writer.addDocument(document);
                    writer.commit();
                } finally {
                    writer.close();
                }
            }
        } finally {
            if (in != null) { 
                try { in.close(); } catch (IOException ex) { /* do nothing */ }
            }
        }
    }
	
	public boolean exists(final String source) throws IOException {
        final IndexReader reader = getIndexReader();
        final IndexSearcher searcher = new IndexSearcher(reader);

        try {            
            return searcher.search(new TermQuery(
                new Term(LuceneDocumentMetadata.SOURCE_FIELD, source)), 1).totalHits > 0;
        } finally {
            reader.close();
        }
    }
	
	public IndexReader getIndexReader() throws IOException {
		 return DirectoryReader.open(directory);
	}
	
	public IndexWriter getIndexWriter() throws IOException {
        return new IndexWriter(directory, new IndexWriterConfig(Version.LUCENE_4_9, analyzer));
    }
}
