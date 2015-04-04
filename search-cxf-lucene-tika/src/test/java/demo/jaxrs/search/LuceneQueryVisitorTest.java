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

package demo.jaxrs.search;

import static demo.jaxrs.search.LuceneQueryMatcher.termQuery;
import static demo.jaxrs.search.LuceneQueryMatcher.phraseQuery;
import static demo.jaxrs.search.LuceneQueryMatcher.wildcardQuery;
import static demo.jaxrs.search.LuceneQueryMatcher.numericRangeQuery;
import static demo.jaxrs.search.LuceneQueryMatcher.termRangeQuery;
import static demo.jaxrs.search.LuceneQueryMatcher.booleanQuery;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

import org.apache.cxf.jaxrs.ext.search.SearchBean;
import org.apache.cxf.jaxrs.ext.search.SearchConditionParser;
import org.apache.cxf.jaxrs.ext.search.fiql.FiqlParser;
import org.apache.cxf.jaxrs.ext.search.odata.ODataParser;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.util.Version;
import org.junit.Before;
import org.junit.Test;

public class LuceneQueryVisitorTest {
    private Analyzer analyzer;
    private SearchConditionParser<SearchBean> fiqlParser;
    private SearchConditionParser<SearchBean> odataParser;
    
    
    @Before
    public void setUp() throws Exception {
        analyzer = new StandardAnalyzer(Version.LUCENE_4_9);
        fiqlParser = new FiqlParser<SearchBean>(SearchBean.class);
        odataParser = new ODataParser<SearchBean>(SearchBean.class);
    }
    
	@Test
	public void termQueryExample() {
		assertThat(fiqlParser.parse("firstName==Bob"), is(termQuery(analyzer, "firstName:bob")));
		assertThat(odataParser.parse("firstName eq 'Bob'"), is(termQuery(analyzer, "firstName:bob")));
	}
	
	@Test
	public void phraseQueryExample() {
		assertThat(fiqlParser.parse("content=='Lucene in Action'"), is(phraseQuery(analyzer, "content:\"lucene ? action\"")));
		assertThat(odataParser.parse("content eq 'Lucene in Action'"), is(phraseQuery(analyzer, "content:\"lucene ? action\"")));
	}
	
	@Test
	public void wildcardQueryExample() {
		assertThat(fiqlParser.parse("firstName==Bo*"), is(wildcardQuery(analyzer, "firstName:Bo*")));
		assertThat(odataParser.parse("firstName eq 'Bo*'"), is(wildcardQuery(analyzer, "firstName:Bo*")));
	}
	
	@Test
	public void numericRangeQueryExample() {
		assertThat(fiqlParser.parse("age=gt=35"), is(numericRangeQuery(analyzer, "age:{35 TO *}")));
		assertThat(odataParser.parse("age gt 35"), is(numericRangeQuery(analyzer, "age:{35 TO *}")));
	}
	
	@Test
	public void termRangeQueryExample() {
		assertThat(fiqlParser.parse("modified=lt=2015-10-25"), is(termRangeQuery(analyzer, "modified:{* TO 20151025040000000}")));
		assertThat(odataParser.parse("modified lt '2015-10-25'"), is(termRangeQuery(analyzer, "modified:{* TO 20151025040000000}")));
	}
	
	@Test
	public void booleanQueryExample() {
		assertThat(fiqlParser.parse("firstName==Bob;age=gt=35"), is(booleanQuery(analyzer, "+firstName:bob +age:{35 TO *}")));
		assertThat(odataParser.parse("firstName eq 'Bob' and age gt 35"), is(booleanQuery(analyzer, "+firstName:bob +age:{35 TO *}")));
	}
}