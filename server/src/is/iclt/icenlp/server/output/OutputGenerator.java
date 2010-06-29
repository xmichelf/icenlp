package is.iclt.icenlp.server.output;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;

import is.iclt.icenlp.IceParser.IceParser;
import is.iclt.icenlp.common.configuration.Configuration;
import is.iclt.icenlp.core.utils.MappingLexicon;
import is.iclt.icenlp.core.utils.Word;
import is.iclt.icenlp.icetagger.IceTagger;
import is.iclt.icenlp.icetagger.IceTaggerException;

import org.apertium.lttoolbox.process.FSTProcessor;

public class OutputGenerator {

	// Private member variables.
	private Configuration configuration;
	private MappingLexicon mapperLexicon = null;
	private String taggingOutputForamt = null;
	private boolean lemmatize = false;
	private boolean leave_not_found_tag_unchanged = false;
	private String not_found_tag = null;
	private String punctuationSeparator = " ";
	private String taggingOutputSparator = " ";
	private FSTProcessor fstp = null;
	private IceTagger iceTagger;
	private IceParser iceParser;

	public OutputGenerator() throws Exception{
		// Get instance of IceTagger.
		this.iceTagger = IceTagger.instance();
		
		// Store the reference to the configuration in member to
		// minimize function calls to getInstance().
		this.configuration = Configuration.getInstance();

		if (this.configuration.containsKey("PunctuationSeparator"))
			this.punctuationSeparator = this.configuration.getValue("PunctuationSeparator");

		if (this.configuration.containsKey("TaggingOutputSparator"))
			this.taggingOutputSparator = this.configuration.getValue("TaggingOutputSparator");

		if (this.configuration.containsKey("compiled_bidix")) {
			String compiledBidixLocation = this.configuration.getValue("compiled_bidix");
			File file = new File(compiledBidixLocation);

			if (!file.exists() || !file.canRead())
				System.out.println("[!!] Unable to read compiled bidix " + compiledBidixLocation + ".");
			
			else {
				System.out.println("[i] Loading compiled bidix " + compiledBidixLocation + ".");
				this.fstp = new FSTProcessor();
				try {
					fstp.load(new BufferedInputStream(new FileInputStream(compiledBidixLocation)));
					System.out.println("[i] Compiled bidix loaded");
				} catch (FileNotFoundException e) {
					this.fstp = null;
					System.out.println("[!!] Unable to read compiled bidix " + compiledBidixLocation + ".");
				} catch (IOException e) {
					System.out.println("[!!] Unable to read compiled bidix " + compiledBidixLocation + ".");
					this.fstp = null;
				}
				fstp.initBiltrans();
				System.out.println("[i] FST for compiled bidix ready.");
			}
		}
		
		// check for not found tag. If there is no unfound_tag set in the
		// configuration file we use a default one: <NOT MAPPED>.
		if (this.configuration.containsKey("unfound_tag"))
			this.not_found_tag = this.configuration.getValue("unfound_tag");
		else
			this.not_found_tag = "<NOT MAPPED>";
		
		// Check for leave_not_found_tag_unchanged in the configuration file.
		if (this.configuration.containsKey("leave_not_found_tag_unchanged")) {
			if (this.configuration.getValue("leave_not_found_tag_unchanged").equals("true")) {
				this.leave_not_found_tag_unchanged = true;
				System.out.println("[i] unfound tags in tagmapping will be left unchanged if not found in mapping collection.");
			} 
			else if (this.configuration.getValue("leave_not_found_tag_unchanged").equals("false")) {
				this.leave_not_found_tag_unchanged = false;
				System.out.println("[i] unfound tags in tagmapping will be marked with " + this.not_found_tag);
			} else {
				System.out.println("[x] leave_not_found_tag_unchanged can either be true or false. Set to default (true).");
				this.leave_not_found_tag_unchanged = true;
			}
		}
		
		// Check for the tagging output
		if (this.configuration.containsKey("taggingoutputformat")) {
			this.taggingOutputForamt = this.configuration.getValue("taggingoutputformat");

			if (this.taggingOutputForamt.contains("[LEMMA]")){
				this.lemmatize = true;
			}

			System.out.println("[i] tagging output format: "+ this.taggingOutputForamt + '.');
		}
		
		try {
			// Check for a mappinglexicon lexicon configuration entry.
			if (this.configuration.containsKey("mappinglexicon")) {
				String mappingLexicon = this.configuration.getValue("mappinglexicon");

				if (mappingLexicon.toLowerCase().equals("icenlp")) {
					System.out.println("[i] Reading mapping lexicon from IceNLP resource.");
					this.mapperLexicon = new MappingLexicon(true, this.leave_not_found_tag_unchanged, this.configuration.debugMode(), this.not_found_tag);
				} else {
					System.out.println("[i] Reading mapping lexicon from: " + mappingLexicon + '.');
					this.mapperLexicon = new MappingLexicon(mappingLexicon,
							true, this.leave_not_found_tag_unchanged,
							this.configuration.debugMode(), this.not_found_tag);
				}

				if(this.configuration.containsKey("leave_lexemes_of_length_one_unchanged")){
					if (this.configuration.getValue("leave_lexemes_of_length_one_unchanged").toLowerCase().equals("true")){
						this.mapperLexicon.setLeave_lexemes_of_length_one_unchanged(true);
						System.out.println("[i] Leave Unknown lexemes of length 1 enabled.");
					}
					else
						System.out.println("[i] Leave Unknown lexemes of length 1 disabled.");
				}
			}

			if (this.lemmatize) 
				this.iceTagger.lemmatize(true);
			
			
			if (this.taggingOutputForamt.contains("[FUNC]"))
				this.iceParser = IceParser.instance();

		}
		catch (Exception e) {
			throw e;
		}
	}
	
	public String generateOutput(String text) 
	{
		if (text.length() == 0 || text.matches("^\\s+$"))
			return "";

		List<Word> wordList;
		try 
		{
			wordList = this.iceTagger.tag(text);
		} 
		catch (IceTaggerException e) {
			e.printStackTrace();
			return "";
		}
		
		if(this.iceParser != null)
			this.iceParser.parse(wordList);

		// Apply mapping rules to the word list.
		if (this.mapperLexicon != null)
			this.mapperLexicon.processWordList(wordList);


		// Create output string that will be sent to the client.
		StringBuilder builder = new StringBuilder();
		
		// If we have not set any tagging output
		if (this.taggingOutputForamt == null) {
			for (Word word : wordList) {
				if (word.linkedToPreviousWord)
					builder.append(punctuationSeparator + word.getLexeme() + " " + word.getTag());

				else
					builder.append(punctuationSeparator + word.getLexeme() + " " + word.getTag());
				
			}
		}

		// We have some tagging output set.
		else {
			boolean nextSlash = false;
			for (Word word : wordList) 
			{

				String part = null;

				
				if (word.getLexeme().equals("\\"))
				{
					if(!nextSlash){
						nextSlash = true;
						continue;
					}
				}
				
				
				
				if (word.isOnlyOutputLexeme())
				{
					if(nextSlash){
						word.setLexeme("\\" + word.getLexeme());
						word.linkedToPreviousWord = true;
						nextSlash = false;
					}
					part = word.getLexeme();
				}
				
				else {
					part = this.taggingOutputForamt.replace("[LEXEME]", word.getLexeme());
					part = part.replace("[TAG]", word.getTag());
					if(this.iceParser != null){
						if(word.parseString == null)
							part = part.replace("[FUNC]", ""); 
						
						else
							part = part.replace("[FUNC]", word.parseString);
					}

					if (this.lemmatize)
						part = part.replace("[LEMMA]", word.getLemma());

					if (this.fstp != null && !word.isOnlyOutputLexeme()) {
						String check = "^" + word.getLemma() + word.getTag() + "$";
						String res = fstp.biltrans(check, true);
						if (res.startsWith("^@")) {
							if (this.configuration.debugMode()){
								System.out.println("[debug] word " + word.getLemma() + " not found in bidix");
								System.out.println(word.getLexeme().length());
							}

							part = this.taggingOutputForamt.replace("[LEXEME]", word.getLexeme());
							part = part.replace("[LEMMA]", "*" + word.getLexeme());
							part = part.replace("[TAG]", "");
							if(word.parseString !=null)
								part = part.replace("[FUNC]", word.parseString);
							else
								part = part.replace("[FUNC]", "");
								
						}
					}
				}

				if (word.linkedToPreviousWord)
					builder.append(punctuationSeparator + part);
					
				else
					builder.append(taggingOutputSparator + part);
				
			}
		}

		// Remove the first char if it is a space.
		if (builder.toString().charAt(0) == ' ')
			return builder.toString().substring(1);

		return builder.toString();
		}	
}