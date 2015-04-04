package demo.jaxrs.search;

import java.sql.Date;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.cxf.jaxrs.ext.search.SearchBean;
import org.apache.cxf.jaxrs.ext.search.SearchCondition;
import org.apache.cxf.jaxrs.ext.search.lucene.LuceneQueryVisitor;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.NumericRangeQuery;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TermRangeQuery;
import org.apache.lucene.search.WildcardQuery;
import org.hamcrest.Description;
import org.hamcrest.Factory;
import org.hamcrest.TypeSafeMatcher;

public class LuceneQueryMatcher extends TypeSafeMatcher< SearchCondition< SearchBean > > {
	private final String expression;
	private final LuceneQueryVisitor< SearchBean > visitor;
	private final Class< ? extends Query > queryClass;
	
	public LuceneQueryMatcher(final Analyzer analyzer, final String expression, final Class< ? extends Query > queryClass) {
		this.expression = expression;
		this.visitor = new LuceneQueryVisitor< SearchBean >(analyzer);
		this.visitor.setContentsFieldMap(Collections.singletonMap("content", "content"));
		
		final Map< String, Class< ? > > primitiveFieldTypeMap = new HashMap<>();
		primitiveFieldTypeMap.put("age", Integer.class);
		primitiveFieldTypeMap.put("modified", Date.class);
		this.visitor.setPrimitiveFieldTypeMap(primitiveFieldTypeMap);
		
		this.queryClass = queryClass;
	}
	
	@Override
	protected void describeMismatchSafely(final SearchCondition<SearchBean> item, final Description description) {
		final Query query = getQuery(item);		
		description.appendText("was ")
			.appendValue(query)
			.appendText("of ")
			.appendText(query.getClass().getSimpleName());
	}
	
	@Override
	public void describeTo(Description description) {
		description.appendValue(expression).appendText("of ").appendText(queryClass.getSimpleName());
	}

	@Override
	protected boolean matchesSafely(final SearchCondition< SearchBean > item) {
		final Query query = getQuery(item);
		return query.toString().equals(this.expression) && queryClass.equals(query.getClass());
	}

	@Factory
	public static LuceneQueryMatcher termQuery(final Analyzer analyzer, final String query) {
		return new LuceneQueryMatcher(analyzer, query, TermQuery.class);
	}
	
	@Factory
	public static LuceneQueryMatcher phraseQuery(final Analyzer analyzer, final String query) {
		return new LuceneQueryMatcher(analyzer, query, PhraseQuery.class);
	}
	
	@Factory
	public static LuceneQueryMatcher wildcardQuery(final Analyzer analyzer, final String query) {
		return new LuceneQueryMatcher(analyzer, query, WildcardQuery.class);
	}

	@Factory
	public static LuceneQueryMatcher numericRangeQuery(final Analyzer analyzer, final String query) {
		return new LuceneQueryMatcher(analyzer, query, NumericRangeQuery.class);
	}
	
	@Factory
	public static LuceneQueryMatcher termRangeQuery(final Analyzer analyzer, final String query) {
		return new LuceneQueryMatcher(analyzer, query, TermRangeQuery.class);
	}
	
	@Factory
	public static LuceneQueryMatcher booleanQuery(final Analyzer analyzer, final String query) {
		return new LuceneQueryMatcher(analyzer, query, BooleanQuery.class);
	}	

	private Query getQuery(final SearchCondition<SearchBean> item) {
		visitor.reset();
		visitor.visit(item);
		return visitor.getQuery();
	}
}
