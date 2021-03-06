/*
 * Copyright (C) 2009 Sverrir Sigmundarson
 *
 * This file is part of the IceNLP toolkit.
 * IceNLP is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * IceNLP is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with IceNLP. If not,  see <http://www.gnu.org/licenses/>.
 *
 * Contact information:
 * Hrafn Loftsson, School of Computer Science, Reykjavik University.
 * hrafn@ru.is
 */
package is.iclt.icenlp.facade;

import is.iclt.icenlp.core.apertium.ApertiumEntry;
import is.iclt.icenlp.core.apertium.ApertiumSegmentizer;
import is.iclt.icenlp.core.apertium.IceNLPTokenConverter;
import is.iclt.icenlp.core.apertium.LtProcParser;
import is.iclt.icenlp.core.icemorphy.IceMorphy;
import is.iclt.icenlp.core.icemorphy.IceMorphyLexicons;
import is.iclt.icenlp.core.icemorphy.IceMorphyResources;
import is.iclt.icenlp.core.icetagger.IceTagger;
import is.iclt.icenlp.core.icetagger.IceTaggerLexicons;
import is.iclt.icenlp.core.icetagger.IceTaggerResources;
import is.iclt.icenlp.core.lemmald.Lemmald;
import is.iclt.icenlp.core.tokenizer.*;
import is.iclt.icenlp.core.tritagger.TriTaggerLexicons;
import is.iclt.icenlp.core.tritagger.TriTagger;
import is.iclt.icenlp.core.tritagger.TriTaggerResources;
import is.iclt.icenlp.core.utils.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;

/**
 * Provides a simplified interface to IceTagger.
 * @author Sverrir Sigmundarson
 */
public class IceTaggerFacade
{
    private TriTagger triTagger = null;
    private IceTagger.HmmModelType modelType = IceTagger.HmmModelType.startend;
    private int sentenceStart = IceTagger.sentenceStartUpperCase;

    private boolean fullDisambiguation = true;
    private boolean initialAssignmentOnly = false;
    // Tokenizer variables
    private boolean strictTokenization = true;
    public String modelPath = "ngrams/models/";

    private Tokenizer tokenizer;
    private IceTagger tagger;
    private Segmentizer segmentizer;
    private Lexicon mapper;
    private IceTaggerLexicons iceLexicons;
    private IceMorphyLexicons morphyLexicons;

    public IceTaggerFacade(IceTaggerLexicons iceLexicons, IceMorphyLexicons morphyLexicons, Lexicon tokenizerLexicon) throws IOException
    {
        segmentizer = new Segmentizer(tokenizerLexicon);
        this.tokenizer = new Tokenizer( Tokenizer.typeIceTokenTags,
                                        strictTokenization,
                                        tokenizerLexicon);
        //this.tokenizer.findMultiWords( false );

        initIceTagger(iceLexicons, morphyLexicons);
        this.iceLexicons = iceLexicons;
        this.morphyLexicons = morphyLexicons;
    }
    
    /*public IceTaggerFacade(IceTaggerLexicons iceLexicons, Lexicon tokenizerLexicon, String morphyLexiconsDictFileWithLocation) throws IOException
    {
    	segmentizer = new Segmentizer(tokenizerLexicon);
        this.tokenizer = new Tokenizer( Tokenizer.typeIceTokenTags, strictTokenization, tokenizerLexicon);
        //this.tokenizer.findMultiWords( false );
        iceLexicons.morphyLexicons.dict = new Lexicon(morphyLexiconsDictFileWithLocation);
        initIceTagger(iceLexicons);
    } */
    
 
    public IceTaggerFacade(IceTaggerLexicons iceLexicons, IceMorphyLexicons morphyLexicons, Lexicon tokenizerLexicon, IceTagger.HmmModelType mType) throws IOException
    {
        this(iceLexicons, morphyLexicons, tokenizerLexicon);
        initHMM(mType);
    }

    public IceTaggerFacade(IceTaggerLexicons iceLexicons, IceMorphyLexicons morphyLexicons, Lexicon tokenizerLexicon,  int lineFormat) throws IOException
    {
        segmentizer = new Segmentizer(tokenizerLexicon, lineFormat);
        this.tokenizer = new Tokenizer( Tokenizer.typeIceTokenTags,
                                        strictTokenization,
                                        tokenizerLexicon);
        //this.tokenizer.findMultiWords( false );
        initIceTagger(iceLexicons, morphyLexicons);

    }

    public IceTaggerFacade(IceTagger.HmmModelType mType) throws IOException
    {
        //this(iceLexicons, tokLexicon);
        this(new IceTaggerLexicons(new IceTaggerResources()), new IceMorphyLexicons(new IceMorphyResources()), new Lexicon(new TokenizerResources().isLexicon));
        initHMM(mType);
    }

    public IceTaggerFacade(IceTaggerLexicons iceLexicons, IceMorphyLexicons morphyLexicons, Lexicon tokenizerLexicon, Lexicon mapperLexicon, boolean preLoadlemmald) throws IOException
    {
        this(iceLexicons, morphyLexicons,tokenizerLexicon);
        this.mapper = mapperLexicon;
        if(preLoadlemmald)
            Lemmald.getInstance();
    }
    
    
    public void setNamedEntityRecognition(boolean flag)
    {
    	this.tagger.setNamedEntityRecognition(flag);
    }

   public void dateHandling( boolean doSpecialDateHandling )
   {
		this.tokenizer.dateHandling(doSpecialDateHandling);
   }

    private void initHMM(IceTagger.HmmModelType mType) throws IOException
    {
          if (mType != IceTagger.HmmModelType.none) {
                // Create an instance of TriTagger
                TriTaggerResources triResources = new TriTaggerResources();
                TriTaggerLexicons triLexicons = new TriTaggerLexicons(triResources, true);
                this.createTriTagger(triLexicons);
                this.setModelType(mType);
        }
    }

    private void initIceTagger(IceTaggerLexicons iceLexicons, IceMorphyLexicons morphyLexicons) {

        IceMorphy morphoAnalyzer = new IceMorphy(
                morphyLexicons.baseDict,
                morphyLexicons.dict,
                morphyLexicons.endingsBase,
                morphyLexicons.endings,
                morphyLexicons.endingsProper,
                morphyLexicons.prefixes,
                morphyLexicons.tagFrequency, null );
        tagger = new IceTagger(sentenceStart, null, morphoAnalyzer,
                morphyLexicons.baseDict,
                morphyLexicons.dict,
                iceLexicons.idioms,
                iceLexicons.verbPrep,
                iceLexicons.verbObj,
                iceLexicons.verbAdverb,
                initialAssignmentOnly,
                fullDisambiguation, triTagger,modelType);
                //fullDisambiguation, triTagger,false);
    }

    public void createTriTagger(TriTaggerLexicons triLexicons) throws IOException
    {
        if (triTagger == null)
        {
			triTagger = new TriTagger( sentenceStart, false, TriTagger.trigrams, triLexicons.ngrams, triLexicons.freqLexicon, null, null, null );
            tagger.setTriTagger(triTagger);
        }
    }

    // For backward compatability - rather use setModelType
    public void useTriTagger(boolean flag) throws IOException
    {
        //tagger.setStartWithHmmModel(flag);
        if (flag)
            tagger.setHmmModelType(IceTagger.HmmModelType.startend);
        else
            tagger.setHmmModelType(IceTagger.HmmModelType.none);
    }

    public void setModelType(IceTagger.HmmModelType mType) {
        tagger.setHmmModelType(mType);
    }

    // This method assumes that one sentence is passed (a Segmentizer is used by the caller)
    public StringBuffer tagSentence(String sentence) throws IOException
    {
        StringBuffer taggedStr = new StringBuffer(256);
        tokenizer.tokenize( sentence );
        if( tokenizer.tokens.size() > 0 )
        {
            tokenizer.splitAbbreviations();
            tagger.tagTokens( tokenizer.tokens );
            for( Object token : tokenizer.tokens )
            {
                IceTokenTags to = (IceTokenTags)token;
                taggedStr.append(to.lexeme);
                taggedStr.append(" ");
                taggedStr.append(to.getFirstTagStr());
                taggedStr.append(" ");
            }
        }
        return taggedStr;
    }

    public Sentences tag( String text ) throws IOException
    {
        Sentence sent=null;
        segmentizer.segmentize( text );

        Sentences sents = new Sentences();

        while( segmentizer.hasMoreSentences() )
        {                                                                   
            String sentenceStr = segmentizer.getNextSentence();

            if( !sentenceStr.equals( "" ) )
            {
                tokenizer.tokenize(sentenceStr);
                if( tokenizer.tokens.size() <= 0 )
                    continue;

				/*ArrayList<Token> tokens;
				tokens = tokenizer.tokens;
				for (Token toki : tokens) {
					System.out.println("gDB>>tokens=("+toki+")");
				} */

                tokenizer.splitAbbreviations();
                tagger.tagTokens( tokenizer.tokens );

                sent = new Sentence(tokenizer.tokens);
                sents.add(sent);
            }
            //if (insertNewline)
            //    segments.add(newLineSegment);
        }

        return sents;
    }
    
    public Pair<IceTokenSentences, ArrayList<ApertiumEntry>> tagExternal(String text, MappingLexicon mappingLexicon) throws IOException
    {
    	IceTokenSentence sent = null;
    	IceTokenSentences sents = new IceTokenSentences();
    	
    	ApertiumSegmentizer segmentizer = new ApertiumSegmentizer(new ByteArrayInputStream(text.getBytes()));
		
		LtProcParser lps;
		ArrayList<ApertiumEntry> entries = null;
		
		IceNLPTokenConverter converter;
		ArrayList<IceTokenTags> tokens;
		
		while(segmentizer.hasMoreSentences())
		{
			// Send the lt-proc string into the parser
			lps = new LtProcParser(segmentizer.getSentance());
			entries = lps.parse();

			// Create the appertium -> iceNLP converter
			//converter = new IceNLPTokenConverter(entries, mappingLexicon, iceLexicons);
            converter = new IceNLPTokenConverter(entries, mappingLexicon, morphyLexicons);
			tokens = converter.convert();

			// Do the actual tagging
			tagger.tagExternalTokens(tokens);
			
			// Convert back reflexive changes that IceTagger did
			converter.changeReflexivePronounTags(tokens);
			
			sent = new IceTokenSentence(tokens);
            sents.add(sent);
			
			segmentizer.processNextSentence();
		}
		
		return new Pair<IceTokenSentences, ArrayList<ApertiumEntry>>(sents, entries);
    }
}
