/*
 * This program's purpose is to read the word vectors written by TrainModel.py and the preprocessed files
 * written by PrintPreprocessedDocuments in order to construct training instances for some learner.
 * The resulting training files are written in SVM light format.
 */


package gov.ornl.stucco.relationprediction;


import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TokensAnnotation;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations.CollapsedCCProcessedDependenciesAnnotation;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.trees.GrammaticalRelation;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeCoreAnnotations.TreeAnnotation;
import edu.stanford.nlp.util.CoreMap;
import gov.ornl.stucco.entity.EntityLabeler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;

public class WriteRelationInstanceFiles
{
	private static String entityextractedfilename;
	//private static String contexts;
	private static boolean training = false;
	
	private static String featuretype;
	
	
	private static DecimalFormat formatter = new DecimalFormat(".0000");
	private static final String emptystringcode = "ZZZ";	//Used to denote a dependency node path of length 0.
	
	
	public static void main(String[] args)
	{
		readArgs(args);
		
		//It is kind of dumb to initialize all the printwriters at once right here, but it made sense in an old version of this program.
		HashMap<Integer,HashMap<String,PrintWriter>> relationtypeTofeaturetypeToprintwriter = initializePrintWriters(featuretype);
		
		buildAndWriteTrainingInstances(featuretype, relationtypeTofeaturetypeToprintwriter);
		
		//Close the file streams.
		for(HashMap<String,PrintWriter> contextToprintwriter : relationtypeTofeaturetypeToprintwriter.values())
			for(PrintWriter pw : contextToprintwriter.values())
				pw.close();
	}
	
	//Arguments: 
	//1. extractedfilename (This is the name of the file written by PrintPreprocessedDocuments.  
	//Valid known values for this argument are "original", "entityreplaced", and "aliasreplaced")
	//2. featuretype (This code (1 character in length) encodes which feature type to build the
	//file from.  To see available feature type character codes and their
	//meanings, see FeatureMap.)
	//3. training (optional).  If you include the word "training" in your command line arguments
	//after the first two (required) feature types, training files (based on the .ser.gz contents
	//of Training DataFiles directory) will be written.  Else testing files (based on the .ser.gz
	//contents of Testing DataFiles directory) will be written.
	private static void readArgs(String[] args)
	{
		entityextractedfilename = args[0];
		featuretype = args[1];

		for(int i = 2; i < args.length; i++)
		{
			if("training".equals(args[i]))
				training = true;
		}
	}
	

	private static HashMap<Integer,HashMap<String,PrintWriter>> initializePrintWriters(String feature)
	{
		HashMap<Integer,HashMap<String,PrintWriter>> relationtypeTocontextToprintwriter = new HashMap<Integer,HashMap<String,PrintWriter>>();
		
		try
		{
				for(Integer i : GenericCyberEntityTextRelationship.getAllRelationshipTypesSet())
				{
					File f = ProducedFileGetter.getRelationshipSVMInstancesFile(entityextractedfilename, feature, i, training);
					
					HashMap<String,PrintWriter> contextToprintwriter = relationtypeTocontextToprintwriter.get(i);
					if(contextToprintwriter == null)
					{
						contextToprintwriter = new HashMap<String,PrintWriter>();
						relationtypeTocontextToprintwriter.put(i, contextToprintwriter);
					}
					contextToprintwriter.put(feature, new PrintWriter(new FileWriter(f)));
				}
		}catch(IOException e)
		{
			System.out.println(e);
			e.printStackTrace();
			System.exit(3);
		}
		
		return relationtypeTocontextToprintwriter;
	}

	
	//Direct the flow to the method for writing the appropriate feature type.
	private static void buildAndWriteTrainingInstances(String featuretype, HashMap<Integer,HashMap<String,PrintWriter>> relationtypeTofeaturetypeToprintwriter)
	{
		if(featuretype.equals(FeatureMap.WORDEMBEDDINGBEFORECONTEXT))
			writeContextFile(featuretype, relationtypeTofeaturetypeToprintwriter);
		if(featuretype.equals(FeatureMap.WORDEMBEDDINGBETWEENCONTEXT))
			writeContextFile(featuretype, relationtypeTofeaturetypeToprintwriter);
		if(featuretype.equals(FeatureMap.WORDEMBEDDINGAFTERCONTEXT))
			writeContextFile(featuretype, relationtypeTofeaturetypeToprintwriter);
		if(featuretype.equals(FeatureMap.SYNTACTICPARSETREEPATH))
			writeParseTreePathFile(featuretype, relationtypeTofeaturetypeToprintwriter);
		if(featuretype.equals(FeatureMap.DEPENDENCYPARSETREEEDGEPATH))
			writeParseTreePathFile(featuretype, relationtypeTofeaturetypeToprintwriter);
		if(featuretype.equals(FeatureMap.DEPENDENCYPARSETREENODEPATH))
			writeParseTreePathFile(featuretype, relationtypeTofeaturetypeToprintwriter);
		if(featuretype.equals(FeatureMap.DEPENDENCYPARSETREEEDGENODEPATH))
			writeParseTreePathFile(featuretype, relationtypeTofeaturetypeToprintwriter);
		if(featuretype.equals(FeatureMap.DEPENDENCYPARSETREENODECONTEXTS))
			writeDependencyContextFile(relationtypeTofeaturetypeToprintwriter);
		if(featuretype.equals(FeatureMap.SYNTACTICPARSETREESUBPATHS))
			writeParseTreePathFile(featuretype, relationtypeTofeaturetypeToprintwriter);
		if(featuretype.equals(FeatureMap.DEPENDENCYPARSETREEEDGESUBPATHS))
			writeParseTreePathFile(featuretype, relationtypeTofeaturetypeToprintwriter);
		if(featuretype.equals(FeatureMap.DEPENDENCYPARSETREENODESUBPATHS))
			writeParseTreePathFile(featuretype, relationtypeTofeaturetypeToprintwriter);
		if(featuretype.equals(FeatureMap.DEPENDENCYPARSETREEEDGENODESUBPATHS))
			writeParseTreePathFile(featuretype, relationtypeTofeaturetypeToprintwriter);
		if(featuretype.equals(FeatureMap.ENTITYBEFORECOUNTS))
			writeEntityContextFile(featuretype, relationtypeTofeaturetypeToprintwriter);
		if(featuretype.equals(FeatureMap.ENTITYBETWEENCOUNTS))
			writeEntityContextFile(featuretype, relationtypeTofeaturetypeToprintwriter);
		if(featuretype.equals(FeatureMap.ENTITYAFTERCOUNTS))
			writeEntityContextFile(featuretype, relationtypeTofeaturetypeToprintwriter);
		if(featuretype.equals(FeatureMap.WORDNGRAMSBEFORE))
			writeWordNgramFile(featuretype, relationtypeTofeaturetypeToprintwriter);
		if(featuretype.equals(FeatureMap.WORDNGRAMSBETWEEN))
			writeWordNgramFile(featuretype, relationtypeTofeaturetypeToprintwriter);
		if(featuretype.equals(FeatureMap.WORDNGRAMSAFTER))
			writeWordNgramFile(featuretype, relationtypeTofeaturetypeToprintwriter);
	}
	
	private static void writeContextFile(String featuretype, HashMap<Integer,HashMap<String,PrintWriter>> relationtypeTocontextToprintwriter)
	{
		//Read in the saved word vectors
		WordToVectorMap wvm = WordToVectorMap.getWordToVectorMap(entityextractedfilename);
		
		try
		{
			//We are switching to using zip files for these because they could potentially be very big.
			ZipFile zipfile = new ZipFile(ProducedFileGetter.getEntityExtractedText(entityextractedfilename, training));
			ZipEntry entry = zipfile.entries().nextElement();
			BufferedReader in = new BufferedReader(new InputStreamReader(zipfile.getInputStream(entry)));

			//We are switching to using zip files for these because they could potentially be very big.
			ZipFile zipfile3 = new ZipFile(ProducedFileGetter.getEntityExtractedText("unlemmatized", training));
			ZipEntry entry3 = zipfile3.entries().nextElement();
			BufferedReader unlemmatizedalignedin = new BufferedReader(new InputStreamReader(zipfile3.getInputStream(entry3)));
			

			InstanceID nextinstanceid;
			Integer nextheuristiclabel = null;
			
			for(Integer relationtype : GenericCyberEntityTextRelationship.getAllRelationshipTypesSet())
			{
				PrintWriter out = relationtypeTocontextToprintwriter.get(relationtype).get(featuretype);
			
				
				File f = ProducedFileGetter.getRelationshipSVMInstancesOrderFile(entityextractedfilename, relationtype, training);
				BufferedReader orderedinstancereader = new BufferedReader(new FileReader(f));
				String nextorderedinstanceline = orderedinstancereader.readLine();
				if(nextorderedinstanceline == null)
					nextinstanceid = null;
				else
				{
					String[] orderedinstancesplitline = nextorderedinstanceline.split(" ");
					nextinstanceid = new InstanceID(orderedinstancesplitline[2]);
					nextheuristiclabel = Integer.parseInt(orderedinstancesplitline[0]);
				}
				
				
				while(nextinstanceid != null)
				{
				//ArrayList<InstanceID> instanceidorder = new ArrayList<InstanceID>(relationtypeToinstanceidorder.get(relationtype));
				//while(instanceidorder.size() > 0)
				//{
					String desiredfilename = nextinstanceid.getFileName();
				
					ArrayList<String[]> filelines = getLinesAssociatedWithOneFile(desiredfilename, in, zipfile, entry);
					ArrayList<String[]> unlemmatizedfilelines = getLinesAssociatedWithOneFile(desiredfilename, unlemmatizedalignedin, zipfile3, entry3);
				
					//These are the indices of the tokens in the original document (before we 
					//replaced entities with representative tokens).
					ArrayList<int[]> originalindices = new ArrayList<int[]>();
					for(String[] sentence : unlemmatizedfilelines)
						originalindices.add(PrintPreprocessedDocuments.getOriginalTokenIndexesFromPreprocessedLine(sentence));
				
					//while(instanceidorder.size() > 0 && instanceidorder.get(0).getFileName().equals(desiredfilename))
					while(nextinstanceid != null && nextinstanceid.getFileName().equals(desiredfilename))
					{
						int firsttokensentencenum = nextinstanceid.getFirstTokenSentenceNum();
						int replacedfirsttokenindex = nextinstanceid.getReplacedFirstTokenIndex();
						int secondtokensentencenum = nextinstanceid.getSecondTokenSentenceNum();
						int replacedsecondtokenindex = nextinstanceid.getReplacedSecondTokenIndex();
					
						String[] firstsentence = filelines.get(firsttokensentencenum);
						String[] secondsentence = filelines.get(secondtokensentencenum);
					
						ArrayList<String> context = new ArrayList<String>();
						if(featuretype.equals(FeatureMap.WORDEMBEDDINGBEFORECONTEXT))
						{
							for(int i = 0; i < replacedfirsttokenindex; i++)
								context.add(firstsentence[i]);
						}
						if(featuretype.equals(FeatureMap.WORDEMBEDDINGAFTERCONTEXT))
						{
							for(int i = replacedsecondtokenindex+1; i < secondsentence.length; i++)
								context.add(secondsentence[i]);
						}
						if(featuretype.equals(FeatureMap.WORDEMBEDDINGBETWEENCONTEXT))
						{
							if(firsttokensentencenum == secondtokensentencenum)
							{
								for(int i = replacedfirsttokenindex+1; i < replacedsecondtokenindex; i++)
									context.add(firstsentence[i]);
							}
							else
							{
								for(int i = replacedfirsttokenindex+1; i < firstsentence.length; i++)
									context.add(firstsentence[i]);
								for(int i = 0; i < replacedsecondtokenindex; i++)
									context.add(secondsentence[i]);
								for(int sentencenum = firsttokensentencenum + 1; sentencenum < secondtokensentencenum; sentencenum++)
								{
									String[] sentence = filelines.get(sentencenum);
									for(String word : sentence)
										context.add(word);
								}
							}
						}
					
						//Now, actually build the SVM_light style string representation of the instance.
						String instanceline = buildOutputLineFromVector(nextheuristiclabel, wvm.getContextVector(context), context.size());
				
						//Add a comment describing what the instance is about (originally written in FindAndOrderAllInstances.
						//String comment = nextorderedinstanceline.split(" # ")[1];
						//String comment = nextorderedinstanceline.substring(nextorderedinstanceline.indexOf('#'));
						String comment = FindAndOrderAllInstances.getCommentFromLine(nextorderedinstanceline);
						instanceline += " # " + comment;
				
						//And finally, print it to the appropriate file using the PrintWriter we 
						//made earlier.
						out.println(instanceline);
					//}
						

						nextorderedinstanceline = orderedinstancereader.readLine();
						if(nextorderedinstanceline == null)
							nextinstanceid = null;
						else
						{
							String[] orderedinstancesplitline = nextorderedinstanceline.split(" ");
							nextinstanceid = new InstanceID(orderedinstancesplitline[2]);
							nextheuristiclabel = Integer.parseInt(orderedinstancesplitline[0]);
						}
					}
				}
				orderedinstancereader.close();
			}
		}catch(IOException e)
		{
			System.out.println(e);
			e.printStackTrace();
			System.exit(3);
		}
	}
	
	
 	public static String buildOutputLineFromVector(Boolean isknownrelationship, double[] contextvectors, int contextlength)
	{
		String result = getLabelStringFromIsKnownRelationship(isknownrelationship);
		
		for(int i = 0; i < contextvectors.length; i++)
			result += " " + (i+1) + ":" + formatter.format(contextvectors[i]);
		result += " " + "TokenCount:" + contextlength;
		
		return result;
	}
 	
 	public static String buildOutputLineFromVector(int label, double[] contextvectors, int contextlength)
 	{
 		if(label == -1)
 			return buildOutputLineFromVector(false, contextvectors, contextlength);
 		else if(label == 1)
 			return buildOutputLineFromVector(true, contextvectors, contextlength);
 		else if(label == 0)
 			return buildOutputLineFromVector(null, contextvectors, contextlength);

		return null;
 	}
 	
 	public static String getLabelStringFromIsKnownRelationship(Boolean isknownrelationship)
 	{
		if(isknownrelationship == null)
			return "0";
		else if(isknownrelationship)
			return "1";
		else
			return "-1";
 	}
	
 	
 	//This method is intended to let us repeatedly find the section of a file beginning with a given line (desiredfilename)
 	//without having to reopen or reread the entirety of the file every time we want to find a section.  
 	public static ArrayList<String[]> getLinesAssociatedWithOneFile(String desiredfilename, BufferedReader in, ZipFile zipfile, ZipEntry entry)
 	{
 		ArrayList<String[]> resultlines = new ArrayList<String[]>();
 		
 		
 		try
 		{
 			try{in.ready();}
 			catch(IOException e){in = new BufferedReader(new InputStreamReader(zipfile.getInputStream(entry)));}
 			
 			
 			String line;
 			while((line = in.readLine()) != null)
 			{
				if(line.startsWith("###") && line.endsWith("###"))
				{
					String currentfilename = line.substring(3, line.length()-3);
					if(currentfilename.equals(desiredfilename))
					{
						while(!(line = in.readLine()).equals(""))
							resultlines.add(line.split(" "));
						break;
					}
				}
 			}
 			
 			if(resultlines.size() == 0)
 			{
 				in.close();
	    		in = new BufferedReader(new InputStreamReader(zipfile.getInputStream(entry)));
	 			while((line = in.readLine()) != null)
	 			{
					if(line.startsWith("###") && line.endsWith("###"))
					{
						String currentfilename = line.substring(3, line.length()-3);
						if(currentfilename.equals(desiredfilename))
						{
							while(!(line = in.readLine()).equals(""))
								resultlines.add(line.split(" "));
							break;
						}
					}
	 			}
 			}
 		}catch(IOException e)
 		{
 			System.out.println(e);
 			e.printStackTrace();
 			System.exit(3);
 		}
 		
 		return resultlines;
 	}
	
 	
 	//This method constructs parse tree paths out of the instances.  It is a good example of how to use annotations provided 
 	//by the entity extractor in the .ser.gz files.
 	public static void writeParseTreePathFile(String featuretype, HashMap<Integer,HashMap<String,PrintWriter>> relationtypeTocontextToprintwriter)
 	{	 
 		String currentfilename = null;
 		List<CoreMap> sentences = null;
 		
		InstanceID nextinstanceid;
		Integer nextheuristiclabel = null;
		String desiredfilename;
 		
		try
		{
			//We're creating these features for each relation type.
			for(Integer relationtype : GenericCyberEntityTextRelationship.getAllRelationshipTypesSet())
			{
				//Construct a PrintWriter that prints to the file for features of this type for this relation type.
				PrintWriter out = relationtypeTocontextToprintwriter.get(relationtype).get(featuretype);
			
				//We enforce a certain order on our instances because for sort of complicated reasons, this means we do not have
				//to hold as many things in memory.  So FindAndOrderAllInstances wrote a file listing all the instances we
				//are interested in using, and we have to process the instances in the order provided by the file.  So read
				//the file it wrote to process the instances in it one-by-one.
				File g = ProducedFileGetter.getRelationshipSVMInstancesOrderFile(entityextractedfilename, relationtype, training);
				BufferedReader orderedinstancereader = new BufferedReader(new FileReader(g));
				String nextorderedinstanceline = orderedinstancereader.readLine();
				if(nextorderedinstanceline == null)
					nextinstanceid = null;
				else
				{
					String[] orderedinstancesplitline = nextorderedinstanceline.split(" ");
					nextinstanceid = new InstanceID(orderedinstancesplitline[2]);
					nextheuristiclabel = Integer.parseInt(orderedinstancesplitline[0]);
				}
			
				//While there are still more instances in the file we're reading:
				while(nextinstanceid != null)
				{
					//There are likely to be many instances associated with each entity-annotated (.ser.gz) file.  Reading 
					//these files is an expensive operation, so we only want to read each file once.  So read file associated'
					//with nextinstanceid in only if we did not already read it in.
					desiredfilename = nextinstanceid.getFileName();
					if(!desiredfilename.equals(currentfilename))
					{
						File f = new File(ProducedFileGetter.getEntityExtractedSerializedDirectory(training), desiredfilename);
						Annotation deserDoc = EntityLabeler.deserializeAnnotatedDoc(f.getAbsolutePath());
						
						//These are the annotated sentences from the entity-annotated file.  They have annotations like POS
						//tags and lemmas, and parse trees, and hopefully in the next version of the VM, coreference and 
						//dependency trees.
						sentences = deserDoc.get(SentencesAnnotation.class);
						
						currentfilename = desiredfilename;
					}
					
					//Get a string representation of the parse tree path between entities represented by nextinstanceid.
					String parsetreepath = null;
					if(featuretype.equals(FeatureMap.SYNTACTICPARSETREEPATH))
						parsetreepath = getSyntacticParseTreePath(nextinstanceid, sentences);
					else if(featuretype.equals(FeatureMap.DEPENDENCYPARSETREEEDGEPATH))
						parsetreepath = getDependencyParseTreePath(featuretype, nextinstanceid, sentences);
					else if(featuretype.equals(FeatureMap.DEPENDENCYPARSETREENODEPATH))
						parsetreepath = getDependencyParseTreePath(featuretype, nextinstanceid, sentences);
					else if(featuretype.equals(FeatureMap.DEPENDENCYPARSETREEEDGENODEPATH))
						parsetreepath = getDependencyParseTreePath(featuretype, nextinstanceid, sentences);
					else if(featuretype.equals(FeatureMap.SYNTACTICPARSETREESUBPATHS))
						parsetreepath = getSyntacticParseTreePath(nextinstanceid, sentences);
					else if(featuretype.equals(FeatureMap.DEPENDENCYPARSETREEEDGESUBPATHS))
						parsetreepath = getDependencyParseTreePath(FeatureMap.DEPENDENCYPARSETREEEDGEPATH, nextinstanceid, sentences);
					else if(featuretype.equals(FeatureMap.DEPENDENCYPARSETREENODESUBPATHS))
						parsetreepath = getDependencyParseTreePath(FeatureMap.DEPENDENCYPARSETREENODEPATH, nextinstanceid, sentences);
					else if(featuretype.equals(FeatureMap.DEPENDENCYPARSETREEEDGENODESUBPATHS))
						parsetreepath = getDependencyParseTreePath(FeatureMap.DEPENDENCYPARSETREEEDGENODEPATH, nextinstanceid, sentences);
					
				
					//Write this instance as a relation instance line consisting of only one feature (the parse tree path)
					//including the heuristic label and comment extracted from the file written by FindAndOrderAllInstances.
					//String comment = nextorderedinstanceline.substring(nextorderedinstanceline.indexOf('#'));
					String comment = FindAndOrderAllInstances.getCommentFromLine(nextorderedinstanceline);
					if(featuretype.equals(FeatureMap.SYNTACTICPARSETREEPATH) || 
							featuretype.equals(FeatureMap.DEPENDENCYPARSETREEEDGEPATH) || 
							featuretype.equals(FeatureMap.DEPENDENCYPARSETREENODEPATH) || 
							featuretype.equals(FeatureMap.DEPENDENCYPARSETREEEDGENODEPATH))
						out.println(nextheuristiclabel + " " + parsetreepath + ":1" + " # " + comment);
					else if(featuretype.equals(FeatureMap.SYNTACTICPARSETREESUBPATHS) || 
							featuretype.equals(FeatureMap.DEPENDENCYPARSETREEEDGESUBPATHS) || 
							featuretype.equals(FeatureMap.DEPENDENCYPARSETREENODESUBPATHS) || 
							featuretype.equals(FeatureMap.DEPENDENCYPARSETREEEDGENODESUBPATHS))
						out.println(nextheuristiclabel + " " + getSubPathFeaturesLine(parsetreepath) + " # " + comment);
				
				
					//Read in the next instance from FindAndOrderAllInstances's file.
					nextorderedinstanceline = orderedinstancereader.readLine();
					if(nextorderedinstanceline == null)
						nextinstanceid = null;
					else
					{
						String[] orderedinstancesplitline = nextorderedinstanceline.split(" ");
						nextinstanceid = new InstanceID(orderedinstancesplitline[2]);
						nextheuristiclabel = Integer.parseInt(orderedinstancesplitline[0]);
					}
				}
				
				out.close();
				orderedinstancereader.close();
			}
		}catch(IOException e)
		{
			System.out.println(e);
			e.printStackTrace();
			System.exit(3);
		}
 	}	
 	
 	//Given an instance, construct a string Comprising the syntactic parse tree labels on the path between the last words in each entity.
 	private static String getSyntacticParseTreePath(InstanceID instanceid, List<CoreMap> sentences)
 	{
 		//sentences is a list of the (annotated) sentences in this document.  Get the sentence in which each entity appears.
 		int firstsentencenum = instanceid.getFirstTokenSentenceNum();
		CoreMap sent1 = sentences.get(firstsentencenum);
		//ArrayList<Tree> pathtoroot1 = getPathToRoot(sent1, instanceid.getFirstTokenEndIndex()-1);

		int secondsentencenum = instanceid.getSecondTokenSentenceNum();
		CoreMap sent2 = sentences.get(secondsentencenum);
		//ArrayList<Tree> pathtoroot2 = getPathToRoot(sent2, instanceid.getSecondTokenEndIndex()-1);
		
		
		String result = "";
		
		//If the entities occur in the same sentence, the path will be directly between the two entities' last words. 
		if(sent1 == sent2)
		{
			Tree tree = sent1.get(TreeAnnotation.class);
			
			List<Tree> leaves = tree.getLeaves();
		        
		    Tree desiredleaf1 = leaves.get(instanceid.getFirstTokenEndIndex()-1);
		    Tree desiredleaf2 = leaves.get(instanceid.getSecondTokenEndIndex()-1);
		            
			List<Tree> path = tree.pathNodeToNode(desiredleaf1, desiredleaf2);
			path.remove(0);
			path.remove(path.size()-1);
			for(Tree node : path)
				result += " " + node.label();
		}
		else	//If the entities do not occur in the same sentence, the path goes all the way up to the root node from the first entity, across the root nodes of all the intervening sentences, then back down from sentence 2's root to entity 2's last word.
		{
			Tree tree1 = sent1.get(TreeAnnotation.class);
			List<Tree> leaves1 = tree1.getLeaves();
			Tree desiredleaf1 = leaves1.get(instanceid.getFirstTokenEndIndex()-1);
			List<Tree> path1 = tree1.pathNodeToNode(desiredleaf1, tree1);
			path1.remove(0);
			
			Tree tree2 = sent2.get(TreeAnnotation.class);
			List<Tree> leaves2 = tree2.getLeaves();
		    Tree desiredleaf2 = leaves2.get(instanceid.getSecondTokenEndIndex()-1);
		    List<Tree> path2 = tree2.pathNodeToNode(tree2, desiredleaf2);
		    path2.remove(path2.size()-1);
		    
		    for(Tree node : path1)
		    	result += " " + node.label();
		    for(int i = 1; i < secondsentencenum - firstsentencenum; i++)
		    	result += " ROOT";
		    for(Tree node : path2)
		    	result += " " + node.label();
		}
		
		return result.trim().replaceAll(" ", "-");
	
		
		/*
		String result = "";
		
		if(sent1 == sent2)
		{
			while(pathtoroot1.get(pathtoroot1.size()-1) == pathtoroot2.get(pathtoroot2.size()-1))
			{
				pathtoroot1.remove(pathtoroot1.size()-1);
				pathtoroot2.remove(pathtoroot2.size()-1);
			}
			
			for(Tree t : pathtoroot1)
				result += " " + t.label();
			result += " " + pathtoroot1.get(pathtoroot1.size()-1).parent().label();
			Collections.reverse(pathtoroot2);
			for(Tree t : pathtoroot2)
				result += " " + t.label();
		}
		else
		{
			for(Tree t : pathtoroot1)
				result += " " + t.label();
			
			for(int i = 1; i < secondsentencenum - firstsentencenum; i++)
				result += " " + pathtoroot1.get(pathtoroot1.size()-1).label();
			
			Collections.reverse(pathtoroot2);
			for(Tree t : pathtoroot2)
				result += " " + t.label();
		}
		
		return result.trim().replaceAll(" ", "-");
		*/
 	}

 	//Given an instance, construct a string Comprising the syntactic parse tree labels on the path between the last words in each entity.
 	private static String getDependencyParseTreePath(String featuretype, InstanceID instanceid, List<CoreMap> sentences)
 	{
 		//sentences is a list of the (annotated) sentences in this document.  Get the sentence in which each entity appears.
 		int firstsentencenum = instanceid.getFirstTokenSentenceNum();
		CoreMap sent1 = sentences.get(firstsentencenum);
		//ArrayList<Tree> pathtoroot1 = getPathToRoot(sent1, instanceid.getFirstTokenEndIndex()-1);

		int secondsentencenum = instanceid.getSecondTokenSentenceNum();
		CoreMap sent2 = sentences.get(secondsentencenum);
		//ArrayList<Tree> pathtoroot2 = getPathToRoot(sent2, instanceid.getSecondTokenEndIndex()-1);
		
		
		
		
		String result = "";
		
		//If the entities occur in the same sentence, the path will be directly between the two entities' last words. 
		if(sent1 == sent2)
		{
			SemanticGraph dependencies1 = sent1.get(CollapsedCCProcessedDependenciesAnnotation.class);
			
			IndexedWord iw1 = dependencies1.getNodeByIndexSafe(instanceid.getFirstTokenEndIndex());	//The +1s are because the nodes this method looks through are 1-indexed, apparently.
			IndexedWord iw2 = dependencies1.getNodeByIndexSafe(instanceid.getSecondTokenEndIndex());
			
			List<IndexedWord> pathwords1 = dependencies1.getShortestUndirectedPathNodes(iw1, iw2);
			for(int i = 1; i < pathwords1.size(); i++)
			{
				SemanticGraphEdge sge = dependencies1.getEdge(pathwords1.get(i-1), pathwords1.get(i));
				
				if(sge == null)
				{
					sge = dependencies1.getEdge(pathwords1.get(i), pathwords1.get(i-1));
					if(featuretype.equals(FeatureMap.DEPENDENCYPARSETREEEDGEPATH) || featuretype.equals(FeatureMap.DEPENDENCYPARSETREEEDGENODEPATH))
						result += " E" + sge.getRelation().getShortName() + "<";	//E is for Edge.
				}
				else
				{
					if(featuretype.equals(FeatureMap.DEPENDENCYPARSETREEEDGEPATH) || featuretype.equals(FeatureMap.DEPENDENCYPARSETREEEDGENODEPATH))
						result += " E" + sge.getRelation().getShortName() + ">";
				}
				
				if(i != pathwords1.size()-1)
					if(featuretype.equals(FeatureMap.DEPENDENCYPARSETREENODEPATH) || featuretype.equals(FeatureMap.DEPENDENCYPARSETREEEDGENODEPATH))
						result += " N" + pathwords1.get(i).lemma().toLowerCase();	//N is for Node.
			}
		}
		else	//If the entities do not occur in the same sentence, the path goes all the way up to the root node from the first entity, across the root nodes of all the intervening sentences, then back down from sentence 2's root to entity 2's last word.
		{
			SemanticGraph dependencies1 = sent1.get(CollapsedCCProcessedDependenciesAnnotation.class);
			IndexedWord iw1 = dependencies1.getNodeByIndexSafe(instanceid.getFirstTokenEndIndex());	//The +1s are because the nodes this method looks through are 1-indexed, apparently.
			
			List<IndexedWord> pathwords1 = dependencies1.getPathToRoot(iw1);
			for(int i = 1; i < pathwords1.size(); i++)
			{
				SemanticGraphEdge sge = dependencies1.getEdge(pathwords1.get(i-1), pathwords1.get(i));
				if(sge == null)
				{
					sge = dependencies1.getEdge(pathwords1.get(i), pathwords1.get(i-1));
					if(featuretype.equals(FeatureMap.DEPENDENCYPARSETREEEDGEPATH) || featuretype.equals(FeatureMap.DEPENDENCYPARSETREEEDGENODEPATH))
						result += " E" + sge.getRelation().getShortName() + "<";
				}
				else
				{
					if(featuretype.equals(FeatureMap.DEPENDENCYPARSETREEEDGEPATH) || featuretype.equals(FeatureMap.DEPENDENCYPARSETREEEDGENODEPATH))
						result += " E" + sge.getRelation().getShortName() + ">";
				}
				
				if(featuretype.equals(FeatureMap.DEPENDENCYPARSETREENODEPATH) || featuretype.equals(FeatureMap.DEPENDENCYPARSETREEEDGENODEPATH))
					result += " N" + pathwords1.get(i).lemma().toLowerCase();
			}
			
			
			 for(int i = 1; i < secondsentencenum - firstsentencenum; i++)
				 result+= " ROOTEDGE";
			

			SemanticGraph dependencies2 = sent2.get(CollapsedCCProcessedDependenciesAnnotation.class);
			IndexedWord iw2 = dependencies2.getNodeByIndexSafe(instanceid.getSecondTokenEndIndex());
			
			List<IndexedWord> pathwords2 = dependencies2.getPathToRoot(iw2);
			Collections.reverse(pathwords2);
			for(int i = 1; i < pathwords2.size(); i++)
			{
				SemanticGraphEdge sge = dependencies2.getEdge(pathwords2.get(i-1), pathwords2.get(i));
				if(sge == null)
				{
					sge = dependencies2.getEdge(pathwords2.get(i), pathwords2.get(i-1));
					if(featuretype.equals(FeatureMap.DEPENDENCYPARSETREEEDGEPATH) || featuretype.equals(FeatureMap.DEPENDENCYPARSETREEEDGENODEPATH))
						result += " E" + sge.getRelation().getShortName() + "<";
				}
				else
				{
					if(featuretype.equals(FeatureMap.DEPENDENCYPARSETREEEDGEPATH) || featuretype.equals(FeatureMap.DEPENDENCYPARSETREEEDGENODEPATH))
						result += " E" + sge.getRelation().getShortName() + ">";
				}
				
				if(featuretype.equals(FeatureMap.DEPENDENCYPARSETREENODEPATH) || featuretype.equals(FeatureMap.DEPENDENCYPARSETREEEDGENODEPATH))
					result += " N" + pathwords2.get(i-1).lemma().toLowerCase();
			}
		}
		
		result = result.trim().replaceAll(" ", "-");
		if(result.equals(""))
			result = emptystringcode;
		return result;
 	}

 	
 	//This method constructs parse tree paths out of the instances.  It is a good example of how to use annotations provided 
 	//by the entity extractor in the .ser.gz files.
 	public static void writeDependencyContextFile(HashMap<Integer,HashMap<String,PrintWriter>> relationtypeTocontextToprintwriter)
 	{	 
		//Read in the saved word vectors
		WordToVectorMap wvm = WordToVectorMap.getWordToVectorMap(entityextractedfilename);
		
		
 		String currentfilename = null;
 		List<CoreMap> sentences = null;
 		
		InstanceID nextinstanceid;
		Integer nextheuristiclabel = null;
		String desiredfilename;
 		
		try
		{
			//We're creating these features for each relation type.
			for(Integer relationtype : GenericCyberEntityTextRelationship.getAllRelationshipTypesSet())
			{
				//Construct a PrintWriter that prints to the file for features of this type for this relation type.
				PrintWriter out = relationtypeTocontextToprintwriter.get(relationtype).get(featuretype);
			
				//We enforce a certain order on our instances because for sort of complicated reasons, this means we do not have
				//to hold as many things in memory.  So FindAndOrderAllInstances wrote a file listing all the instances we
				//are interested in using, and we have to process the instances in the order provided by the file.  So read
				//the file it wrote to process the instances in it one-by-one.
				File g = ProducedFileGetter.getRelationshipSVMInstancesOrderFile(entityextractedfilename, relationtype, training);
				BufferedReader orderedinstancereader = new BufferedReader(new FileReader(g));
				String nextorderedinstanceline = orderedinstancereader.readLine();
				if(nextorderedinstanceline == null)
					nextinstanceid = null;
				else
				{
					String[] orderedinstancesplitline = nextorderedinstanceline.split(" ");
					nextinstanceid = new InstanceID(orderedinstancesplitline[2]);
					nextheuristiclabel = Integer.parseInt(orderedinstancesplitline[0]);
				}
			
				//While there are still more instances in the file we're reading:
				while(nextinstanceid != null)
				{
					//There are likely to be many instances associated with each entity-annotated (.ser.gz) file.  Reading 
					//these files is an expensive operation, so we only want to read each file once.  So read file associated'
					//with nextinstanceid in only if we did not already read it in.
					desiredfilename = nextinstanceid.getFileName();
					if(!desiredfilename.equals(currentfilename))
					{
						File f = new File(ProducedFileGetter.getEntityExtractedSerializedDirectory(training), desiredfilename);
						Annotation deserDoc = EntityLabeler.deserializeAnnotatedDoc(f.getAbsolutePath());
						
						//These are the annotated sentences from the entity-annotated file.  They have annotations like POS
						//tags and lemmas, and parse trees, and hopefully in the next version of the VM, coreference and 
						//dependency trees.
						sentences = deserDoc.get(SentencesAnnotation.class);
						
						currentfilename = desiredfilename;
					}
					
					
					//Get a string representation of the parse tree path between entities represented by nextinstanceid.
					String nodepath = getDependencyParseTreePath(FeatureMap.DEPENDENCYPARSETREENODEPATH, nextinstanceid, sentences);
					String[] lemmas = {};
					if(!nodepath.equals(emptystringcode))
						lemmas = nodepath.split("-");
					
					ArrayList<String> context = new ArrayList<String>();
					for(String lemma : lemmas)
						context.add(lemma);
					
					
					//Now, actually build the SVM_light style string representation of the instance.
					String instanceline = buildOutputLineFromVector(nextheuristiclabel, wvm.getContextVector(context), context.size());
			
					
					//Write this instance as a relation instance line consisting of only one feature (the parse tree path)
					//including the heuristic label and comment extracted from the file written by FindAndOrderAllInstances.
					//String comment = nextorderedinstanceline.substring(nextorderedinstanceline.indexOf('#'));
					String comment = FindAndOrderAllInstances.getCommentFromLine(nextorderedinstanceline);
					out.println(instanceline + " # " + comment);
				
				
					//Read in the next instance from FindAndOrderAllInstances's file.
					nextorderedinstanceline = orderedinstancereader.readLine();
					if(nextorderedinstanceline == null)
						nextinstanceid = null;
					else
					{
						String[] orderedinstancesplitline = nextorderedinstanceline.split(" ");
						nextinstanceid = new InstanceID(orderedinstancesplitline[2]);
						nextheuristiclabel = Integer.parseInt(orderedinstancesplitline[0]);
					}
				}
				
				out.close();
				orderedinstancereader.close();
			}
		}catch(IOException e)
		{
			System.out.println(e);
			e.printStackTrace();
			System.exit(3);
		}
 	}	
	
 	
 	private static String getSubPathFeaturesLine(String parsetreepath)
 	{
 		HashSet<String> features = new HashSet<String>();
 		
 		String[] splitpath = parsetreepath.split("-");
 		for(int i = 0; i < splitpath.length; i++)
 		{
 			for(int j = i+1; j <= splitpath.length && j <= i+5; j++)
 			{
 				String feature = "";
 				for(int k = i; k < j; k++)
 					feature += " " + splitpath[k];
 				feature = feature.trim().replaceAll(" ", "-");
 				
 				features.add(feature);
 			}
 		}
 		
 		ArrayList<String> sortedfeatures = new ArrayList<String>(features);
 		Collections.sort(sortedfeatures);
 		String result = "";
 		for(String feature : sortedfeatures)
 			result += " " + feature + ":1";
 		
 		return result.trim();
 	}


	private static void writeEntityContextFile(String featuretype, HashMap<Integer,HashMap<String,PrintWriter>> relationtypeTocontextToprintwriter)
	{
		try
		{
			//We are switching to using zip files for these because they could potentially be very big.
			ZipFile zipfile = new ZipFile(ProducedFileGetter.getEntityExtractedText(entityextractedfilename, training));
			ZipEntry entry = zipfile.entries().nextElement();
			BufferedReader in = new BufferedReader(new InputStreamReader(zipfile.getInputStream(entry)));

			//We are switching to using zip files for these because they could potentially be very big.
			ZipFile zipfile3 = new ZipFile(ProducedFileGetter.getEntityExtractedText("unlemmatized", training));
			ZipEntry entry3 = zipfile3.entries().nextElement();
			BufferedReader unlemmatizedalignedin = new BufferedReader(new InputStreamReader(zipfile3.getInputStream(entry3)));
			

			InstanceID nextinstanceid;
			Integer nextheuristiclabel = null;
			
			for(Integer relationtype : GenericCyberEntityTextRelationship.getAllRelationshipTypesSet())
			{
				PrintWriter out = relationtypeTocontextToprintwriter.get(relationtype).get(featuretype);
			
				
				File f = ProducedFileGetter.getRelationshipSVMInstancesOrderFile(entityextractedfilename, relationtype, training);
				BufferedReader orderedinstancereader = new BufferedReader(new FileReader(f));
				String nextorderedinstanceline = orderedinstancereader.readLine();
				if(nextorderedinstanceline == null)
					nextinstanceid = null;
				else
				{
					String[] orderedinstancesplitline = nextorderedinstanceline.split(" ");
					nextinstanceid = new InstanceID(orderedinstancesplitline[2]);
					nextheuristiclabel = Integer.parseInt(orderedinstancesplitline[0]);
				}
				
				
				while(nextinstanceid != null)
				{
				//ArrayList<InstanceID> instanceidorder = new ArrayList<InstanceID>(relationtypeToinstanceidorder.get(relationtype));
				//while(instanceidorder.size() > 0)
				//{
					String desiredfilename = nextinstanceid.getFileName();
				
					ArrayList<String[]> filelines = getLinesAssociatedWithOneFile(desiredfilename, in, zipfile, entry);
					ArrayList<String[]> unlemmatizedfilelines = getLinesAssociatedWithOneFile(desiredfilename, unlemmatizedalignedin, zipfile3, entry3);
				
					//These are the indices of the tokens in the original document (before we 
					//replaced entities with representative tokens).
					ArrayList<int[]> originalindices = new ArrayList<int[]>();
					for(String[] sentence : unlemmatizedfilelines)
						originalindices.add(PrintPreprocessedDocuments.getOriginalTokenIndexesFromPreprocessedLine(sentence));
				
					//Figure out which features should go with this instance.
					//while(instanceidorder.size() > 0 && instanceidorder.get(0).getFileName().equals(desiredfilename))
					while(nextinstanceid != null && nextinstanceid.getFileName().equals(desiredfilename))
					{
						int firsttokensentencenum = nextinstanceid.getFirstTokenSentenceNum();
						int replacedfirsttokenindex = nextinstanceid.getReplacedFirstTokenIndex();
						int secondtokensentencenum = nextinstanceid.getSecondTokenSentenceNum();
						int replacedsecondtokenindex = nextinstanceid.getReplacedSecondTokenIndex();
					
						String[] firstsentence = filelines.get(firsttokensentencenum);
						String[] secondsentence = filelines.get(secondtokensentencenum);
					
						//Figure out which words appear in the context we are interested in.
						ArrayList<String> context = new ArrayList<String>();
						if(featuretype.equals(FeatureMap.ENTITYBEFORECOUNTS))
						{
							for(int i = 0; i < replacedfirsttokenindex; i++)
								context.add(firstsentence[i]);
						}
						if(featuretype.equals(FeatureMap.ENTITYAFTERCOUNTS))
						{
							for(int i = replacedsecondtokenindex+1; i < secondsentence.length; i++)
								context.add(secondsentence[i]);
						}
						if(featuretype.equals(FeatureMap.ENTITYBETWEENCOUNTS))
						{
							if(firsttokensentencenum == secondtokensentencenum)
							{
								for(int i = replacedfirsttokenindex+1; i < replacedsecondtokenindex; i++)
									context.add(firstsentence[i]);
							}
							else
							{
								for(int i = replacedfirsttokenindex+1; i < firstsentence.length; i++)
									context.add(firstsentence[i]);
								for(int i = 0; i < replacedsecondtokenindex; i++)
									context.add(secondsentence[i]);
								for(int sentencenum = firsttokensentencenum + 1; sentencenum < secondtokensentencenum; sentencenum++)
								{
									String[] sentence = filelines.get(sentencenum);
									for(String word : sentence)
										context.add(word);
								}
							}
						}
						
						
						//Count entities of each type in the set of words we collected.
						HashMap<String,Integer> entitytypecounts = new HashMap<String,Integer>();
						for(String word : context)
						{
							CyberEntityText cet = CyberEntityText.getCyberEntityTextFromToken(word);
							if(cet != null)
							{
								String entitytypestring = CyberEntityText.entitytypeindexToentitytypename.get(cet.getEntityType());
								Integer oldcount = entitytypecounts.get(entitytypestring);
								if(oldcount == null)
									oldcount = 0;
								entitytypecounts.put(entitytypestring, oldcount+1);
							}
						}
						
						
						String instanceline = nextheuristiclabel + "";
						ArrayList<String> sortedentities = new ArrayList<String>(entitytypecounts.keySet());
						Collections.sort(sortedentities);
						for(String entitystring : sortedentities)
							instanceline += " " + entitystring + ":" + entitytypecounts.get(entitystring);
					
						//Now, actually build the SVM_light style string representation of the instance.
						//String instanceline = buildOutputLineFromVector(nextheuristiclabel, wvm.getContextVector(context), context.size());
				
						//Add a comment describing what the instance is about (originally written in FindAndOrderAllInstances.
						//String comment = nextorderedinstanceline.substring(nextorderedinstanceline.indexOf('#'));
						String comment = FindAndOrderAllInstances.getCommentFromLine(nextorderedinstanceline);
						instanceline += " # " + comment;
				
						//And finally, print it to the appropriate file using the PrintWriter we 
						//made earlier.
						out.println(instanceline);
					//}
						

						nextorderedinstanceline = orderedinstancereader.readLine();
						if(nextorderedinstanceline == null)
							nextinstanceid = null;
						else
						{
							String[] orderedinstancesplitline = nextorderedinstanceline.split(" ");
							nextinstanceid = new InstanceID(orderedinstancesplitline[2]);
							nextheuristiclabel = Integer.parseInt(orderedinstancesplitline[0]);
						}
					}
				}
				orderedinstancereader.close();
			}
		}catch(IOException e)
		{
			System.out.println(e);
			e.printStackTrace();
			System.exit(3);
		}
	}

	
	private static void writeWordNgramFile(String featuretype, HashMap<Integer,HashMap<String,PrintWriter>> relationtypeTocontextToprintwriter)
	{
		try
		{
			//We are switching to using zip files for these because they could potentially be very big.
			ZipFile zipfile = new ZipFile(ProducedFileGetter.getEntityExtractedText(entityextractedfilename, training));
			ZipEntry entry = zipfile.entries().nextElement();
			BufferedReader in = new BufferedReader(new InputStreamReader(zipfile.getInputStream(entry)));

			//We are switching to using zip files for these because they could potentially be very big.
			ZipFile zipfile3 = new ZipFile(ProducedFileGetter.getEntityExtractedText("unlemmatized", training));
			ZipEntry entry3 = zipfile3.entries().nextElement();
			BufferedReader unlemmatizedalignedin = new BufferedReader(new InputStreamReader(zipfile3.getInputStream(entry3)));
			

			InstanceID nextinstanceid;
			Integer nextheuristiclabel = null;
			
			for(Integer relationtype : GenericCyberEntityTextRelationship.getAllRelationshipTypesSet())
			{
				PrintWriter out = relationtypeTocontextToprintwriter.get(relationtype).get(featuretype);
			
				
				File f = ProducedFileGetter.getRelationshipSVMInstancesOrderFile(entityextractedfilename, relationtype, training);
				BufferedReader orderedinstancereader = new BufferedReader(new FileReader(f));
				String nextorderedinstanceline = orderedinstancereader.readLine();
				if(nextorderedinstanceline == null)
					nextinstanceid = null;
				else
				{
					String[] orderedinstancesplitline = nextorderedinstanceline.split(" ");
					nextinstanceid = new InstanceID(orderedinstancesplitline[2]);
					nextheuristiclabel = Integer.parseInt(orderedinstancesplitline[0]);
				}
				
				
				while(nextinstanceid != null)
				{
				//ArrayList<InstanceID> instanceidorder = new ArrayList<InstanceID>(relationtypeToinstanceidorder.get(relationtype));
				//while(instanceidorder.size() > 0)
				//{
					String desiredfilename = nextinstanceid.getFileName();
				
					ArrayList<String[]> filelines = getLinesAssociatedWithOneFile(desiredfilename, in, zipfile, entry);
					ArrayList<String[]> unlemmatizedfilelines = getLinesAssociatedWithOneFile(desiredfilename, unlemmatizedalignedin, zipfile3, entry3);
				
					//These are the indices of the tokens in the original document (before we 
					//replaced entities with representative tokens).
					ArrayList<int[]> originalindices = new ArrayList<int[]>();
					for(String[] sentence : unlemmatizedfilelines)
						originalindices.add(PrintPreprocessedDocuments.getOriginalTokenIndexesFromPreprocessedLine(sentence));
				
					//Figure out which features should go with this instance.
					//while(instanceidorder.size() > 0 && instanceidorder.get(0).getFileName().equals(desiredfilename))
					while(nextinstanceid != null && nextinstanceid.getFileName().equals(desiredfilename))
					{
						int firsttokensentencenum = nextinstanceid.getFirstTokenSentenceNum();
						int replacedfirsttokenindex = nextinstanceid.getReplacedFirstTokenIndex();
						int secondtokensentencenum = nextinstanceid.getSecondTokenSentenceNum();
						int replacedsecondtokenindex = nextinstanceid.getReplacedSecondTokenIndex();
					
						String[] firstsentence = filelines.get(firsttokensentencenum);
						String[] secondsentence = filelines.get(secondtokensentencenum);
					
						//Figure out which words appear in the context we are interested in.
						ArrayList<String> context = new ArrayList<String>();
						if(featuretype.equals(FeatureMap.WORDNGRAMSBEFORE))
						{
							for(int i = 0; i < replacedfirsttokenindex; i++)
								context.add(firstsentence[i]);
						}
						if(featuretype.equals(FeatureMap.WORDNGRAMSAFTER))
						{
							for(int i = replacedsecondtokenindex+1; i < secondsentence.length; i++)
								context.add(secondsentence[i]);
						}
						if(featuretype.equals(FeatureMap.WORDNGRAMSBETWEEN))
						{
							if(firsttokensentencenum == secondtokensentencenum)
							{
								for(int i = replacedfirsttokenindex+1; i < replacedsecondtokenindex; i++)
									context.add(firstsentence[i]);
							}
							else
							{
								for(int i = replacedfirsttokenindex+1; i < firstsentence.length; i++)
									context.add(firstsentence[i]);
								for(int i = 0; i < replacedsecondtokenindex; i++)
									context.add(secondsentence[i]);
								for(int sentencenum = firsttokensentencenum + 1; sentencenum < secondtokensentencenum; sentencenum++)
								{
									String[] sentence = filelines.get(sentencenum);
									for(String word : sentence)
										context.add(word);
								}
							}
						}
						
						
						HashSet<String> featurenames = new HashSet<String>();
						for(int n = 1; n <= 3; n++)
						{
							for(int i = 0; i < context.size(); i++)
							{
								String featurename = "";
								for(int j = i; j < i + n && j < context.size(); j++)
									featurename += " " + context.get(j).replaceAll("-", "+");
								featurename = featurename.replaceAll("-", "+").trim().replaceAll(" ", "-");
								featurenames.add(featurename);
							}
						}
						ArrayList<String> sortedfeaturenames = new ArrayList<String>(featurenames);
						Collections.sort(sortedfeaturenames);
						
						
						String instanceline = nextheuristiclabel + "";
						for(String featurename : sortedfeaturenames)
							instanceline += " " + featurename+ ":1";
					
						//Now, actually build the SVM_light style string representation of the instance.
						//String instanceline = buildOutputLineFromVector(nextheuristiclabel, wvm.getContextVector(context), context.size());
				
						//Add a comment describing what the instance is about (originally written in FindAndOrderAllInstances.
						//String comment = nextorderedinstanceline.substring(nextorderedinstanceline.indexOf('#'));
						String comment = FindAndOrderAllInstances.getCommentFromLine(nextorderedinstanceline);
						instanceline += " # " + comment;
				
						//And finally, print it to the appropriate file using the PrintWriter we 
						//made earlier.
						out.println(instanceline);
					//}
						

						nextorderedinstanceline = orderedinstancereader.readLine();
						if(nextorderedinstanceline == null)
							nextinstanceid = null;
						else
						{
							String[] orderedinstancesplitline = nextorderedinstanceline.split(" ");
							nextinstanceid = new InstanceID(orderedinstancesplitline[2]);
							nextheuristiclabel = Integer.parseInt(orderedinstancesplitline[0]);
						}
					}
				}
				orderedinstancereader.close();
			}
		}catch(IOException e)
		{
			System.out.println(e);
			e.printStackTrace();
			System.exit(3);
		}
	}

	
	public static String[] getInstanceAndComments(String line)
	{
		return line.split(" # ");
	}
	
	
}
