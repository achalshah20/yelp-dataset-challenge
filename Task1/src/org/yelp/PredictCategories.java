package org.yelp;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.print.attribute.standard.JobName;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.similarities.ClassicSimilarity;
import org.apache.lucene.search.similarities.LMDirichletSimilarity;
import org.apache.lucene.search.similarities.LMJelinekMercerSimilarity;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.store.FSDirectory;

import com.opencsv.CSVWriter;

public class PredictCategories {

	public static void main(String args[]) {

		try {
			
			String queryPath  = "../data/categories.csv";
			ArrayList<String> categoriesList = new ArrayList<String>();
			
			try(BufferedReader br = new BufferedReader(new FileReader(queryPath))) {
			    for(String line; (line = br.readLine()) != null; ) {
			    	categoriesList.add(line);
			    }
			    // line is not visible here.
			}
			
			//System.out.println(categoriesList);
			
			//categoriesList.add("ab");
			
			System.out.println("Total number of queries: " + categoriesList.size());
			
			HashMap<String,HashMap<String,Float>> BM25CategoryPreds = findQuery(new BM25Similarity(), categoriesList, "run-1", "BM25 Algorithm");
			HashMap<String,HashMap<String,Float>> ClassicCategoryPreds = findQuery(new ClassicSimilarity(), categoriesList, "run-1", "Classic Algorithm");
			HashMap<String,HashMap<String,Float>>  LMDCategoryPreds= findQuery(new LMDirichletSimilarity(), categoriesList, "run-1", "LMD Algorithm");
			HashMap<String,HashMap<String,Float>> LMJMCategoryPreds = findQuery(new LMJelinekMercerSimilarity(.7f), categoriesList, "run-1", "LMJM Algorithm");

			businessToCatMapping(BM25CategoryPreds,"BM25results.csv");
			businessToCatMapping(ClassicCategoryPreds,"Classicresults.csv");

			businessToCatMapping(LMDCategoryPreds,"LMDresults.csv");
			businessToCatMapping(LMJMCategoryPreds,"LMJMesults.csv");

		
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	public static HashMap<String,HashMap<String,Float>>  findQuery(Similarity sAlgo, List<String> queryList, String runID, String outFileName)
			throws IOException, ParseException {

		System.out.println("Finding results using " + outFileName );
		String index = "indexes";
		IndexReader reader = DirectoryReader.open(FSDirectory.open(Paths.get(index)));
		IndexSearcher searcher = new IndexSearcher(reader);

		Analyzer analyzer = new StandardAnalyzer();
		searcher.setSimilarity(sAlgo);
		QueryParser parser = new QueryParser("Text", analyzer);

		StringBuilder allResults = new StringBuilder();
		HashMap<String,HashMap<String,Float>> categoryPredictions = new HashMap<String,HashMap<String,Float>>();
		for (String queryString : queryList) {
			// String queryString = "police";

			Query query = parser.parse(QueryParser.escape(queryString));
			//System.out.println("Searching for: " + query.toString("Text"));

			TopDocs results = searcher.search(query, 300);

			// Print number of hits
			int numTotalHits = results.totalHits;
			//System.out.println(numTotalHits + " total matching documents");

			// Print retrieved results
			ScoreDoc[] hits = results.scoreDocs;
			int rank = 1;
			
			HashMap<String, Float> businessScores = new HashMap<String,Float>();
			for (int i = 0; i < hits.length; i++) {
				// System.out.println("doc=" + hits[i].doc + " score=" +
				// hits[i].score);
				Document doc = searcher.doc(hits[i].doc);
				String businessId = doc.get("BusinessID");
				//System.out.println(businessId);
				Float score = hits[i].score;
				String result = doc.get("BusinessID") + ": "+ hits[i].score;
				allResults.append(result);
				allResults.append("\n");
				
				if(categoryPredictions.containsKey(businessId)){
					HashMap<String,Float> catToScoreMap = categoryPredictions.get(businessId);
					catToScoreMap.put(queryString, score);
					categoryPredictions.put(businessId,catToScoreMap);
				}else{
					HashMap<String,Float> catToScoreMap = new HashMap<String,Float>();
					catToScoreMap.put(queryString, score);
					categoryPredictions.put(businessId,catToScoreMap);
				}
				
				
				// System.out.println("TEXT: "+doc.get("TEXT"));
				//System.out.println(result);
				rank++;
			
			}
			

		}

		reader.close();
		
		return categoryPredictions;

	}
	
		public static void businessToCatMapping(HashMap<String,HashMap<String,Float>> catMapping,String fileName) throws IOException{
		
			System.out.println("Writing results in " + fileName);
			for(Map.Entry<String, HashMap<String,Float>> outer : catMapping.entrySet()){
				//System.out.println("Business Id: " + outer.getKey());
				//System.out.println();
				HashMap<String, Float> innerMap = outer.getValue();
				Map<String, Float> sortedMap = sortByValue(innerMap);
				//System.out.println(sortedMap);
				int n =0;
				CSVWriter csvWriter= new CSVWriter(new FileWriter(fileName, true));
				ArrayList arrayList=new ArrayList<>();
				arrayList.add(outer.getKey());
				for(Map.Entry<String, Float> inner : sortedMap.entrySet()){
					if(n<=2){
					arrayList.add(inner.getKey());
					//System.out.print("Category: " + inner.getKey() + "Value  "  + inner.getValue());
					n++;
					}
				}
				Object[] a = arrayList.toArray();
				String[] res = Arrays.copyOf( a, a.length, String[].class);
				if(a[0].toString().length() < 30){
					csvWriter.writeNext(res);
					csvWriter.close();
				}else{
					//System.out.println(a[0]);

				}
				
				//System.out.println();
		}
			
			

	
		}
		
		public static <K, V extends Comparable<? super V>> Map<K, V> 
	    sortByValue( Map<K, V> map )
	{
	    List<Map.Entry<K, V>> list =
	        new LinkedList<>( map.entrySet() );
	    Collections.sort( list, new Comparator<Map.Entry<K, V>>()
	    {
	        @Override
	        public int compare( Map.Entry<K, V> o1, Map.Entry<K, V> o2 )
	        {
	            return -1*(( o1.getValue() ).compareTo( o2.getValue() ));
	        }
	    } );

	    Map<K, V> result = new LinkedHashMap<>();
	    for (Map.Entry<K, V> entry : list)
	    {
	        result.put( entry.getKey(), entry.getValue() );
	    }
	    return result;
	}

}
