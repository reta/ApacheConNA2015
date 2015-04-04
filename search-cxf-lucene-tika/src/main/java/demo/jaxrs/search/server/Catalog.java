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


import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.activation.DataHandler;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObjectBuilder;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.StreamingOutput;
import javax.ws.rs.core.UriInfo;

import org.apache.cxf.common.util.StringUtils;
import org.apache.cxf.helpers.IOUtils;
import org.apache.cxf.jaxrs.ext.multipart.Attachment;
import org.apache.cxf.jaxrs.ext.multipart.MultipartBody;
import org.apache.cxf.jaxrs.ext.search.SearchBean;
import org.apache.cxf.jaxrs.ext.search.SearchContext;
import org.apache.cxf.jaxrs.ext.search.lucene.LuceneQueryVisitor;
import org.apache.cxf.jaxrs.ext.search.tika.LuceneDocumentMetadata;
import org.apache.cxf.jaxrs.ext.search.tika.TikaLuceneContentExtractor;
import org.apache.cxf.rs.security.cors.CrossOriginResourceSharing;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.util.Version;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.odf.OpenDocumentParser;
import org.apache.tika.parser.pdf.PDFParser;
import org.apache.tika.parser.txt.TXTParser;

@Path("/catalog")
public class Catalog {
    private final TikaLuceneContentExtractor extractor = new TikaLuceneContentExtractor(
        Arrays.< Parser >asList(new PDFParser(), new TXTParser(), new OpenDocumentParser()), 
        new LuceneDocumentMetadata());    
    
    private final Analyzer analyzer = new StandardAnalyzer(Version.LUCENE_4_9);    
    private final Storage storage; 
    private final Indexer indexer;
    private final LuceneQueryVisitor<SearchBean> visitor;
    private final ExecutorService executor = Executors.newFixedThreadPool(
        Runtime.getRuntime().availableProcessors());
    
    public Catalog() throws IOException {
        this(new Storage());
    }
    
    public Catalog(final Storage storage) throws IOException {
        this.storage = storage;
        this.indexer = new Indexer(extractor, storage, analyzer);
        
        final Map< String, Class< ? > > fieldTypes = new HashMap< String, Class< ? > >();
        fieldTypes.put("modified", Date.class);
        fieldTypes.put("xmpTPg:NPages", Integer.class);
        
        visitor = new LuceneQueryVisitor<SearchBean>("ct", "contents", analyzer);
        visitor.setPrimitiveFieldTypeMap(fieldTypes);
    }
    
    @POST
    @CrossOriginResourceSharing(allowAllOrigins = true)
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public void addBook(@Suspended final AsyncResponse response, @Context final UriInfo uri, final MultipartBody body) {
        executor.submit(new Runnable() {
            public void run() {
                for (final Attachment attachment: body.getAllAttachments()) {
                    final DataHandler handler =  attachment.getDataHandler();
                    
                    if (handler != null) {
                        final String source = handler.getName();
                        if (StringUtils.isEmpty(source)) {
                            response.resume(Response.status(Status.BAD_REQUEST).build());
                            return;
                        }
                                                
                        final LuceneDocumentMetadata metadata = new LuceneDocumentMetadata()
                            .withSource(source)
                            .withField("modified", Date.class)
                            .withField("xmpTPg:NPages", Integer.class);
                        
                        try {
                            if (indexer.exists(source)) {
                                response.resume(Response.status(Status.CONFLICT).build());
                                return;
                            }

                            final byte[] content = IOUtils.readBytesFromStream(handler.getInputStream());
                            indexer.storeAndIndex(metadata, content);
                        } catch (final Exception ex) {
                            response.resume(Response.serverError().build());  
                        } 
                        
                        if (response.isSuspended()) {
                            response.resume(Response.created(uri.getRequestUriBuilder().path(source).build()).build());
                        }
                    }                       
                }              
                
                if (response.isSuspended()) {
                    response.resume(Response.status(Status.BAD_REQUEST).build());
                }
            }
        });
    }
    
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public JsonArray getBooks() throws IOException {
        final IndexReader reader = indexer.getIndexReader();
        final IndexSearcher searcher = new IndexSearcher(reader);
        final JsonArrayBuilder builder = Json.createArrayBuilder();
        
        try {
            final Query query = new MatchAllDocsQuery();
            
            for (final ScoreDoc scoreDoc: searcher.search(query, 1000).scoreDocs) {
                final Document document = reader.document(scoreDoc.doc);
                
                final JsonObjectBuilder element = Json.createObjectBuilder();
                for (final IndexableField field: document.getFields()) {
                	if (!field.name().equalsIgnoreCase("contents")) {
                		element.add(field.name(), field.stringValue());
                	}
                }
                
                builder.add(element);
            }
            
            return builder.build();
        } finally {
            reader.close();
        }
    }
    
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @CrossOriginResourceSharing(allowAllOrigins = true)
    @Path("/search")
    public Response findBook(@Context SearchContext searchContext, @Context UriInfo uri) throws IOException {        
        final IndexReader reader = indexer.getIndexReader();
        final IndexSearcher searcher = new IndexSearcher(reader);
        final JsonArrayBuilder builder = Json.createArrayBuilder();

        try {            
            visitor.reset();
            visitor.visit(searchContext.getCondition(SearchBean.class));
            
            final Query query = visitor.getQuery();            
            if (query != null) {
                final TopDocs topDocs = searcher.search(query, 1000);
                for (final ScoreDoc scoreDoc: topDocs.scoreDocs) {
                    final Document document = reader.document(scoreDoc.doc);
                    final String source = document
                        .getField(LuceneDocumentMetadata.SOURCE_FIELD)
                        .stringValue();
                    
                    builder.add(
                        Json.createObjectBuilder()
                            .add("source", source)
                            .add("score", scoreDoc.score)
                            .add("url", uri.getBaseUriBuilder()
                                    .path(Catalog.class)
                                    .path(source)
                                    .build().toString())
                    );
                }
            }
            
            return Response.ok(builder.build()).build();
        } finally {
            reader.close();
        }
    }
    
    @GET
    @Path("/{source}")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public StreamingOutput getBook(@PathParam("source") final String source) throws IOException {            
        return new StreamingOutput() {            
            @Override
            public void write(final OutputStream out) throws IOException, WebApplicationException {
                InputStream in = null;
                
                try {
                    in = storage.getDocument(source);
                    out.write(IOUtils.readBytesFromStream(in));
                } catch (final FileNotFoundException ex) {
                    throw new NotFoundException("Document does not exist: " + source);
                } finally {
                    if (in != null) { 
                        try { in.close(); } catch (IOException ex) { /* do nothing */ }
                    }    
                }                
            }
        };
    }
    
    @DELETE
    public Response delete() throws IOException {
        final IndexWriter writer = indexer.getIndexWriter();
        
        try {
            storage.deleteAll();
            writer.deleteAll();
            writer.commit();
        } finally {
            writer.close();
        }  
        
        return Response.ok().build();
    }   
}


