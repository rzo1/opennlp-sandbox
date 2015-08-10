/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package opennlp.tools.disambiguator.lesk;

import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import opennlp.tools.disambiguator.Constants;
import opennlp.tools.disambiguator.Loader;
import opennlp.tools.disambiguator.SynNode;
import opennlp.tools.disambiguator.PreProcessor;
import opennlp.tools.disambiguator.WSDParameters;
import opennlp.tools.disambiguator.WSDSample;
import opennlp.tools.disambiguator.WSDisambiguator;
import opennlp.tools.disambiguator.WordPOS;
import opennlp.tools.disambiguator.WordSense;
import opennlp.tools.disambiguator.mfs.MFS;
import opennlp.tools.util.Span;
import net.sf.extjwnl.JWNLException;
import net.sf.extjwnl.data.Synset;
import net.sf.extjwnl.data.Word;

/**
 * Implementation of the <b>Overlap Of Senses</b> approach originally proposed
 * by Lesk. The main idea is to check for word overlaps in the sense definitions
 * of the surrounding context. An overlap is when two words have similar stems.
 * The more overlaps a word has the higher its score. Different variations of
 * the approach are included in this class.
 * 
 */
public class Lesk implements WSDisambiguator {

  /**
   * The lesk specific parameters
   */
  protected LeskParameters params;
  /**
   * List of filtered context words
   */
  ArrayList<WordPOS> contextWords = new ArrayList<WordPOS>();

  public Lesk() {
    this(null);
  }

  /**
   * Initializes the loader object and sets the input parameters
   * 
   * @param Input
   *          Parameters
   * @throws InvalidParameterException
   */
  public Lesk(LeskParameters params) throws InvalidParameterException {
    this.setParams(params);
  }

  /**
   * If the parameters are null set the default ones, else only set them if they
   * valid. Invalid parameters will return a exception
   * 
   * @param Input
   *          parameters
   * @throws InvalidParameterException
   */
  @Override
  public void setParams(WSDParameters params) throws InvalidParameterException {
    if (params == null) {
      this.params = new LeskParameters();
    } else {
      if (params.isValid()) {
        this.params = (LeskParameters) params;
      } else {
        throw new InvalidParameterException("wrong params");
      }
    }
  }

  /**
   * @return the parameter settings
   */
  public LeskParameters getParams() {
    return params;
  }

  /**
   * The basic Lesk method where the entire context is considered for overlaps
   * 
   * @param The
   *          word to disambiguate
   * @return The array of WordSenses with their scores
   */
  public ArrayList<WordSense> basic(WSDSample sample) {

    WordPOS word = new WordPOS(sample.getTargetWord(), sample.getTargetTag());

    ArrayList<Synset> synsets = word.getSynsets();
    ArrayList<SynNode> nodes = new ArrayList<SynNode>();

    for (int i = 0; i < sample.getSentence().length; i++) {
      contextWords
          .add(new WordPOS(sample.getSentence()[i], sample.getTags()[i]));
    }
    for (Synset synset : synsets) {
      SynNode node = new SynNode(synset, contextWords);
      nodes.add(node);
    }

    ArrayList<WordSense> scoredSenses = SynNode.updateSenses(nodes);

    for (WordSense wordSense : scoredSenses) {
      wordSense.setWSDSample(sample);
      int count = 0;
      for (WordPOS senseWordPOS : wordSense.getNode().getSenseRelevantWords()) {
        for (WordPOS sentenceWordPOS : contextWords) {
          if (sentenceWordPOS.isStemEquivalent(senseWordPOS)) {
            count = count + 1;
          }
        }
      }
      wordSense.setScore(count);
    }

    return scoredSenses;
  }

  /**
   * The basic Lesk method but applied to a default context windows
   * 
   * @param The
   *          word to disambiguate
   * @return The array of WordSenses with their scores
   */
  public ArrayList<WordSense> basicContextual(WSDSample sample) {
    return this.basicContextual(sample, LeskParameters.DFLT_WIN_SIZE);
  }

  /**
   * The basic Lesk method but applied to a custom context windows
   * 
   * @param The
   *          word to disambiguate
   * @param windowSize
   * @return The array of WordSenses with their scores
   */
  public ArrayList<WordSense> basicContextual(WSDSample sample, int windowSize) {
    return this.basicContextual(sample, windowSize, windowSize);
  }

  /**
   * The basic Lesk method but applied to a context windows set by custom
   * backward and forward window lengths
   * 
   * @param wtd
   *          the word to disambiguate
   * @param windowBackward
   * @return the array of WordSenses with their scores
   */
  public ArrayList<WordSense> basicContextual(WSDSample sample,
      int windowBackward, int windowForward) {

    WordPOS word = new WordPOS(sample.getTargetWord(), sample.getTargetTag());

    ArrayList<Synset> synsets = word.getSynsets();
    ArrayList<SynNode> nodes = new ArrayList<SynNode>();

    int index = sample.getTargetPosition();

    for (int i = index - windowBackward; i <= index + windowForward; i++) {
      if (i >= 0 && i < sample.getSentence().length && i != index) {
        contextWords.add(new WordPOS(sample.getSentence()[i],
            sample.getTags()[i]));
      }
    }

    for (Synset synset : synsets) {
      SynNode node = new SynNode(synset, contextWords);
      nodes.add(node);
    }

    ArrayList<WordSense> scoredSenses = SynNode.updateSenses(nodes);

    for (WordSense wordSense : scoredSenses) {
      wordSense.setWSDSample(sample);

      int count = 0;
      for (WordPOS senseWordPOS : wordSense.getNode().getSenseRelevantWords()) {

        for (WordPOS sentenceWordPOS : contextWords) {
          // TODO change to lemma check
          if (sentenceWordPOS.isStemEquivalent(senseWordPOS)) {
            count = count + 1;
          }
        }

      }
      wordSense.setScore(count);

    }
    return scoredSenses;
  }

  /**
   * An extended version of the Lesk approach that takes into consideration
   * semantically related feature overlaps across the entire context The scoring
   * function uses linear weights.
   * 
   * @param wtd
   *          the word to disambiguate
   * @param depth
   *          how deep to go into each feature tree
   * @param depthScoreWeight
   *          the weighing per depth level
   * @param includeSynonyms
   * @param includeHypernyms
   * @param includeHyponyms
   * @param includeMeronyms
   * @param includeHolonyms
   * @return the array of WordSenses with their scores
   */
  public ArrayList<WordSense> extended(WSDSample sample, int depth,
      double depthScoreWeight, boolean includeSynonyms,
      boolean includeHypernyms, boolean includeHyponyms,
      boolean includeMeronyms, boolean includeHolonyms) {

    return extendedContextual(sample, 0, depth, depthScoreWeight,
        includeSynonyms, includeHypernyms, includeHyponyms, includeMeronyms,
        includeHolonyms);

  }

  /**
   * An extended version of the Lesk approach that takes into consideration
   * semantically related feature overlaps in a default context window The
   * scoring function uses linear weights.
   * 
   * @param wtd
   *          the word to disambiguate
   * @param depth
   *          how deep to go into each feature tree
   * @param depthScoreWeight
   *          the weighing per depth level
   * @param includeSynonyms
   * @param includeHypernyms
   * @param includeHyponyms
   * @param includeMeronyms
   * @param includeHolonyms
   * @return the array of WordSenses with their scores
   */
  public ArrayList<WordSense> extendedContextual(WSDSample sample, int depth,
      double depthScoreWeight, boolean includeSynonyms,
      boolean includeHypernyms, boolean includeHyponyms,
      boolean includeMeronyms, boolean includeHolonyms) {

    return extendedContextual(sample, LeskParameters.DFLT_WIN_SIZE, depth,
        depthScoreWeight, includeSynonyms, includeHypernyms, includeHyponyms,
        includeMeronyms, includeHolonyms);

  }

  /**
   * An extended version of the Lesk approach that takes into consideration
   * semantically related feature overlaps in a custom context window The
   * scoring function uses linear weights.
   * 
   * @param wtd
   *          the word to disambiguate
   * @param windowSize
   *          the custom context window size
   * @param depth
   *          how deep to go into each feature tree
   * @param depthScoreWeight
   *          the weighing per depth level
   * @param includeSynonyms
   * @param includeHypernyms
   * @param includeHyponyms
   * @param includeMeronyms
   * @param includeHolonyms
   * @return the array of WordSenses with their scores
   */
  public ArrayList<WordSense> extendedContextual(WSDSample sample,
      int windowSize, int depth, double depthScoreWeight,
      boolean includeSynonyms, boolean includeHypernyms,
      boolean includeHyponyms, boolean includeMeronyms, boolean includeHolonyms) {

    return extendedContextual(sample, windowSize, windowSize, depth,
        depthScoreWeight, includeSynonyms, includeHypernyms, includeHyponyms,
        includeMeronyms, includeHolonyms);
  }

  /**
   * An extended version of the Lesk approach that takes into consideration
   * semantically related feature overlaps in a custom context window The
   * scoring function uses linear weights.
   * 
   * @param wtd
   *          the word to disambiguate
   * @param windowBackward
   *          the custom context backward window size
   * @param windowForward
   *          the custom context forward window size
   * @param depth
   *          how deep to go into each feature tree
   * @param depthScoreWeight
   *          the weighing per depth level
   * @param includeSynonyms
   * @param includeHypernyms
   * @param includeHyponyms
   * @param includeMeronyms
   * @param includeHolonyms
   * @return the array of WordSenses with their scores
   */
  public ArrayList<WordSense> extendedContextual(WSDSample sample,
      int windowBackward, int windowForward, int depth,
      double depthScoreWeight, boolean includeSynonyms,
      boolean includeHypernyms, boolean includeHyponyms,
      boolean includeMeronyms, boolean includeHolonyms) {

    ArrayList<WordSense> scoredSenses = basicContextual(sample, windowBackward,
        windowForward);

    for (WordSense wordSense : scoredSenses) {

      if (includeSynonyms) {
        wordSense.setScore(wordSense.getScore() + depthScoreWeight
            * assessSynonyms(wordSense.getNode().getSynonyms(), contextWords));
      }

      if (includeHypernyms) {
        fathomHypernyms(wordSense, wordSense.getNode().synset, contextWords,
            depth, depth, depthScoreWeight);
      }

      if (includeHyponyms) {
        fathomHyponyms(wordSense, wordSense.getNode().synset, contextWords,
            depth, depth, depthScoreWeight);
      }

      if (includeMeronyms) {
        fathomMeronyms(wordSense, wordSense.getNode().synset, contextWords,
            depth, depth, depthScoreWeight);

      }

      if (includeHolonyms) {
        fathomHolonyms(wordSense, wordSense.getNode().synset, contextWords,
            depth, depth, depthScoreWeight);

      }

    }

    return scoredSenses;

  }

  /**
   * An extended version of the Lesk approach that takes into consideration
   * semantically related feature overlaps in all the context. The scoring
   * function uses exponential weights.
   * 
   * @param wtd
   *          the word to disambiguate
   * @param depth
   *          how deep to go into each feature tree
   * @param intersectionExponent
   * @param depthExponent
   * @param includeSynonyms
   * @param includeHypernyms
   * @param includeHyponyms
   * @param includeMeronyms
   * @param includeHolonyms
   * @return the array of WordSenses with their scores
   */
  public ArrayList<WordSense> extendedExponential(WSDSample sample, int depth,
      double intersectionExponent, double depthExponent,
      boolean includeSynonyms, boolean includeHypernyms,
      boolean includeHyponyms, boolean includeMeronyms, boolean includeHolonyms) {

    return extendedExponentialContextual(sample, 0, depth,
        intersectionExponent, depthExponent, includeSynonyms, includeHypernyms,
        includeHyponyms, includeMeronyms, includeHolonyms);

  }

  /**
   * An extended version of the Lesk approach that takes into consideration
   * semantically related feature overlaps in a default window in the context.
   * The scoring function uses exponential weights.
   * 
   * @param wtd
   *          the word to disambiguate
   * @param depth
   *          how deep to go into each feature tree
   * @param intersectionExponent
   * @param depthExponent
   * @param includeSynonyms
   * @param includeHypernyms
   * @param includeHyponyms
   * @param includeMeronyms
   * @param includeHolonyms
   * @return the array of WordSenses with their scores
   */
  public ArrayList<WordSense> extendedExponentialContextual(WSDSample sample,
      int depth, double intersectionExponent, double depthExponent,
      boolean includeSynonyms, boolean includeHypernyms,
      boolean includeHyponyms, boolean includeMeronyms, boolean includeHolonyms) {

    return extendedExponentialContextual(sample, LeskParameters.DFLT_WIN_SIZE,
        depth, intersectionExponent, depthExponent, includeSynonyms,
        includeHypernyms, includeHyponyms, includeMeronyms, includeHolonyms);
  }

  /**
   * An extended version of the Lesk approach that takes into consideration
   * semantically related feature overlaps in a custom window in the context.
   * The scoring function uses exponential weights.
   * 
   * @param wtd
   *          the word to disambiguate
   * @param windowSize
   * @param depth
   *          how deep to go into each feature tree
   * @param intersectionExponent
   * @param depthExponent
   * @param includeSynonyms
   * @param includeHypernyms
   * @param includeHyponyms
   * @param includeMeronyms
   * @param includeHolonyms
   * @return the array of WordSenses with their scores
   */
  public ArrayList<WordSense> extendedExponentialContextual(WSDSample sample,
      int windowSize, int depth, double intersectionExponent,
      double depthExponent, boolean includeSynonyms, boolean includeHypernyms,
      boolean includeHyponyms, boolean includeMeronyms, boolean includeHolonyms) {

    return extendedExponentialContextual(sample, windowSize, windowSize, depth,
        intersectionExponent, depthExponent, includeSynonyms, includeHypernyms,
        includeHyponyms, includeMeronyms, includeHolonyms);
  }

  /**
   * An extended version of the Lesk approach that takes into consideration
   * semantically related feature overlaps in a custom window in the context.
   * The scoring function uses exponential weights.
   * 
   * @param wtd
   *          the word to disambiguate
   * @param windowBackward
   * @param windowForward
   * @param depth
   * @param intersectionExponent
   * @param depthExponent
   * @param includeSynonyms
   * @param includeHypernyms
   * @param includeHyponyms
   * @param includeMeronyms
   * @param includeHolonyms
   * @return the array of WordSenses with their scores
   */
  public ArrayList<WordSense> extendedExponentialContextual(WSDSample sample,
      int windowBackward, int windowForward, int depth,
      double intersectionExponent, double depthExponent,
      boolean includeSynonyms, boolean includeHypernyms,
      boolean includeHyponyms, boolean includeMeronyms, boolean includeHolonyms) {

    ArrayList<WordSense> scoredSenses = basicContextual(sample, windowForward,
        windowBackward);

    for (WordSense wordSense : scoredSenses) {

      if (includeSynonyms) {
        wordSense.setScore(wordSense.getScore()
            + Math
                .pow(
                    assessSynonyms(wordSense.getNode().getSynonyms(),
                        contextWords), intersectionExponent));
      }

      if (includeHypernyms) {
        fathomHypernymsExponential(wordSense, wordSense.getNode().synset,
            contextWords, depth, depth, intersectionExponent, depthExponent);
      }

      if (includeHyponyms) {
        fathomHyponymsExponential(wordSense, wordSense.getNode().synset,
            contextWords, depth, depth, intersectionExponent, depthExponent);
      }

      if (includeMeronyms) {
        fathomMeronymsExponential(wordSense, wordSense.getNode().synset,
            contextWords, depth, depth, intersectionExponent, depthExponent);

      }

      if (includeHolonyms) {
        fathomHolonymsExponential(wordSense, wordSense.getNode().synset,
            contextWords, depth, depth, intersectionExponent, depthExponent);

      }

    }

    return scoredSenses;

  }

  /**
   * Recursively score the hypernym tree linearly
   * 
   * @param wordSense
   * @param child
   * @param relvWords
   * @param depth
   * @param maxDepth
   * @param depthScoreWeight
   */
  private void fathomHypernyms(WordSense wordSense, Synset child,
      ArrayList<WordPOS> relvWords, int depth, int maxDepth,
      double depthScoreWeight) {
    if (depth == 0)
      return;

    String[] tokenizedGloss = Loader.getTokenizer().tokenize(
        child.getGloss().toString());
    ArrayList<WordPOS> relvGlossWords = PreProcessor
        .getAllRelevantWords(tokenizedGloss);

    SynNode childNode = new SynNode(child, relvGlossWords);

    childNode.setHypernyms();
    wordSense.setScore(wordSense.getScore()
        + Math.pow(depthScoreWeight, maxDepth - depth + 1)
        * assessFeature(childNode.getHypernyms(), relvWords));
    for (Synset hypernym : childNode.getHypernyms()) {
      fathomHypernyms(wordSense, hypernym, relvGlossWords, depth - 1, maxDepth,
          depthScoreWeight);
    }
  }

  /**
   * Recursively score the hypernym tree exponentially
   * 
   * @param wordSense
   * @param child
   * @param relvWords
   * @param depth
   * @param maxDepth
   * @param intersectionExponent
   * @param depthScoreExponent
   */
  private void fathomHypernymsExponential(WordSense wordSense, Synset child,
      ArrayList<WordPOS> relvWords, int depth, int maxDepth,
      double intersectionExponent, double depthScoreExponent) {
    if (depth == 0)
      return;

    String[] tokenizedGloss = Loader.getTokenizer().tokenize(
        child.getGloss().toString());
    ArrayList<WordPOS> relvGlossWords = PreProcessor
        .getAllRelevantWords(tokenizedGloss);

    SynNode childNode = new SynNode(child, relvGlossWords);

    childNode.setHypernyms();
    wordSense.setScore(wordSense.getScore()
        + Math.pow(assessFeature(childNode.getHypernyms(), relvWords),
            intersectionExponent) / Math.pow(depth, depthScoreExponent));
    for (Synset hypernym : childNode.getHypernyms()) {
      fathomHypernymsExponential(wordSense, hypernym, relvGlossWords,
          depth - 1, maxDepth, intersectionExponent, depthScoreExponent);
    }
  }

  /**
   * Recursively score the hyponym tree linearly
   * 
   * @param wordSense
   * @param child
   * @param relvWords
   * @param depth
   * @param maxDepth
   * @param depthScoreWeight
   */
  private void fathomHyponyms(WordSense wordSense, Synset child,
      ArrayList<WordPOS> relvWords, int depth, int maxDepth,
      double depthScoreWeight) {
    if (depth == 0)
      return;

    String[] tokenizedGloss = Loader.getTokenizer().tokenize(
        child.getGloss().toString());
    ArrayList<WordPOS> relvGlossWords = PreProcessor
        .getAllRelevantWords(tokenizedGloss);

    SynNode childNode = new SynNode(child, relvGlossWords);

    childNode.setHyponyms();
    wordSense.setScore(wordSense.getScore()
        + Math.pow(depthScoreWeight, maxDepth - depth + 1)
        * assessFeature(childNode.getHyponyms(), relvWords));
    for (Synset hyponym : childNode.getHyponyms()) {

      fathomHyponyms(wordSense, hyponym, relvGlossWords, depth - 1, maxDepth,
          depthScoreWeight);
    }
  }

  /**
   * Recursively score the hyponym tree exponentially
   * 
   * @param wordSense
   * @param child
   * @param relvWords
   * @param depth
   * @param maxDepth
   * @param intersectionExponent
   * @param depthScoreExponent
   */
  private void fathomHyponymsExponential(WordSense wordSense, Synset child,
      ArrayList<WordPOS> relvWords, int depth, int maxDepth,
      double intersectionExponent, double depthScoreExponent) {
    if (depth == 0)
      return;

    String[] tokenizedGloss = Loader.getTokenizer().tokenize(
        child.getGloss().toString());
    ArrayList<WordPOS> relvGlossWords = PreProcessor
        .getAllRelevantWords(tokenizedGloss);

    SynNode childNode = new SynNode(child, relvGlossWords);

    childNode.setHyponyms();
    wordSense.setScore(wordSense.getScore()
        + Math.pow(assessFeature(childNode.getHyponyms(), relvWords),
            intersectionExponent) / Math.pow(depth, depthScoreExponent));
    for (Synset hyponym : childNode.getHyponyms()) {

      fathomHyponymsExponential(wordSense, hyponym, relvGlossWords, depth - 1,
          maxDepth, intersectionExponent, depthScoreExponent);
    }
  }

  /**
   * Recursively score the meronym tree linearly
   * 
   * @param wordSense
   * @param child
   * @param relvWords
   * @param depth
   * @param maxDepth
   * @param depthScoreWeight
   */
  private void fathomMeronyms(WordSense wordSense, Synset child,
      ArrayList<WordPOS> relvWords, int depth, int maxDepth,
      double depthScoreWeight) {
    if (depth == 0)
      return;

    String[] tokenizedGloss = Loader.getTokenizer().tokenize(
        child.getGloss().toString());
    ArrayList<WordPOS> relvGlossWords = PreProcessor
        .getAllRelevantWords(tokenizedGloss);

    SynNode childNode = new SynNode(child, relvGlossWords);

    childNode.setMeronyms();
    wordSense.setScore(wordSense.getScore()
        + Math.pow(depthScoreWeight, maxDepth - depth + 1)
        * assessFeature(childNode.getMeronyms(), relvWords));
    for (Synset meronym : childNode.getMeronyms()) {

      fathomMeronyms(wordSense, meronym, relvGlossWords, depth - 1, maxDepth,
          depthScoreWeight);
    }
  }

  /**
   * Recursively score the meronym tree exponentially
   * 
   * @param wordSense
   * @param child
   * @param relvWords
   * @param depth
   * @param maxDepth
   * @param intersectionExponent
   * @param depthScoreExponent
   */
  private void fathomMeronymsExponential(WordSense wordSense, Synset child,
      ArrayList<WordPOS> relvWords, int depth, int maxDepth,
      double intersectionExponent, double depthScoreExponent) {
    if (depth == 0)
      return;

    String[] tokenizedGloss = Loader.getTokenizer().tokenize(
        child.getGloss().toString());
    ArrayList<WordPOS> relvGlossWords = PreProcessor
        .getAllRelevantWords(tokenizedGloss);

    SynNode childNode = new SynNode(child, relvGlossWords);

    childNode.setMeronyms();
    wordSense.setScore(wordSense.getScore()
        + Math.pow(assessFeature(childNode.getMeronyms(), relvWords),
            intersectionExponent) / Math.pow(depth, depthScoreExponent));
    for (Synset meronym : childNode.getMeronyms()) {

      fathomMeronymsExponential(wordSense, meronym, relvGlossWords, depth - 1,
          maxDepth, intersectionExponent, depthScoreExponent);
    }
  }

  /**
   * Recursively score the holonym tree linearly
   * 
   * @param wordSense
   * @param child
   * @param relvWords
   * @param depth
   * @param maxDepth
   * @param depthScoreWeight
   */
  private void fathomHolonyms(WordSense wordSense, Synset child,
      ArrayList<WordPOS> relvWords, int depth, int maxDepth,
      double depthScoreWeight) {
    if (depth == 0)
      return;

    String[] tokenizedGloss = Loader.getTokenizer().tokenize(
        child.getGloss().toString());
    ArrayList<WordPOS> relvGlossWords = PreProcessor
        .getAllRelevantWords(tokenizedGloss);

    SynNode childNode = new SynNode(child, relvGlossWords);

    childNode.setHolonyms();
    wordSense.setScore(wordSense.getScore()
        + Math.pow(depthScoreWeight, maxDepth - depth + 1)
        * assessFeature(childNode.getHolonyms(), relvWords));
    for (Synset holonym : childNode.getHolonyms()) {

      fathomHolonyms(wordSense, holonym, relvGlossWords, depth - 1, maxDepth,
          depthScoreWeight);
    }
  }

  /**
   * Recursively score the holonym tree exponentially
   * 
   * @param wordSense
   * @param child
   * @param relvWords
   * @param depth
   * @param maxDepth
   * @param intersectionExponent
   * @param depthScoreExponent
   */
  private void fathomHolonymsExponential(WordSense wordSense, Synset child,
      ArrayList<WordPOS> relvWords, int depth, int maxDepth,
      double intersectionExponent, double depthScoreExponent) {
    if (depth == 0)
      return;

    String[] tokenizedGloss = Loader.getTokenizer().tokenize(
        child.getGloss().toString());
    ArrayList<WordPOS> relvGlossWords = PreProcessor
        .getAllRelevantWords(tokenizedGloss);

    SynNode childNode = new SynNode(child, relvGlossWords);

    childNode.setHolonyms();
    wordSense.setScore(wordSense.getScore()
        + Math.pow(assessFeature(childNode.getHolonyms(), relvWords),
            intersectionExponent) / Math.pow(depth, depthScoreExponent));
    for (Synset holonym : childNode.getHolonyms()) {

      fathomHolonymsExponential(wordSense, holonym, relvGlossWords, depth - 1,
          maxDepth, intersectionExponent, depthScoreExponent);
    }
  }

  /**
   * Checks if the feature should be counted in the score
   * 
   * @param featureSynsets
   * @param relevantWords
   * @return count of features to consider
   */
  private int assessFeature(ArrayList<Synset> featureSynsets,
      ArrayList<WordPOS> relevantWords) {
    int count = 0;
    for (Synset synset : featureSynsets) {
      SynNode subNode = new SynNode(synset, relevantWords);

      String[] tokenizedSense = Loader.getTokenizer().tokenize(
          subNode.getGloss());
      ArrayList<WordPOS> relvSenseWords = PreProcessor
          .getAllRelevantWords(tokenizedSense);

      for (WordPOS senseWord : relvSenseWords) {
        for (WordPOS sentenceWord : relevantWords) {
          if (sentenceWord.isStemEquivalent(senseWord)) {
            count = count + 1;
          }
        }
      }
    }
    return count;
  }

  /**
   * Checks if the synonyms should be counted in the score
   * 
   * @param synonyms
   * @param relevantWords
   * @return count of synonyms to consider
   */
  private int assessSynonyms(ArrayList<WordPOS> synonyms,
      ArrayList<WordPOS> relevantWords) {
    int count = 0;

    for (WordPOS synonym : synonyms) {
      for (WordPOS sentenceWord : relevantWords) {
        if (sentenceWord.isStemEquivalent(synonym)) {
          count = count + 1;
        }
      }
    }
    return count;
  }

  /**
   * Disambiguates an ambiguous word in its context
   * 
   * @param tokenizedContext
   * @param ambiguousTokenIndex
   * @return array of sense indexes from WordNet ordered by their score. The
   *         result format is <b>Source</b> <b>SenseID</b> If the input token is
   *         non relevant a null is returned.
   */
  @Override
  public String[] disambiguate(String[] tokenizedContext, String[] tokenTags,
      int ambiguousTokenIndex, String ambiguousTokenLemma) {
    return disambiguate(new WSDSample(tokenizedContext, tokenTags,
        ambiguousTokenIndex, ambiguousTokenLemma));
  }

  /**
   * Disambiguates an ambiguous word in its context The user can set a span of
   * inputWords from the tokenized input
   * 
   * @param inputText
   * @param inputWordSpans
   * @return array of array of sense indexes from WordNet ordered by their
   *         score. The result format is <b>Source</b> <b>SenseID</b> If the
   *         input token is non relevant a null is returned.
   */
  @Override
  public String[][] disambiguate(String[] tokenizedContext, String[] tokenTags,
      Span ambiguousTokenSpan, String ambiguousTokenLemma) {
    // TODO need to work on spans
    return null;
  }

  @Override
  public String[] disambiguate(WSDSample sample) {
    // if the word is not relevant return null
    if (!Constants.isRelevant(sample.getTargetTag())) {
      return null;
    }

    ArrayList<WordSense> wsenses = null;

    switch (this.params.leskType) {
    case LESK_BASIC:
      wsenses = basic(sample);
      break;
    case LESK_BASIC_CTXT:
      wsenses = basicContextual(sample);
      break;
    case LESK_BASIC_CTXT_WIN:
      wsenses = basicContextual(sample, this.params.win_b_size);
      break;
    case LESK_BASIC_CTXT_WIN_BF:
      wsenses = basicContextual(sample, this.params.win_b_size,
          this.params.win_f_size);
      break;
    case LESK_EXT:
      wsenses = extended(sample, this.params.depth, this.params.depth_weight,
          this.params.fathom_synonyms, this.params.fathom_hypernyms,
          this.params.fathom_hyponyms, this.params.fathom_meronyms,
          this.params.fathom_holonyms);
      break;
    case LESK_EXT_CTXT:
      wsenses = extendedContextual(sample, this.params.depth,
          this.params.depth_weight, this.params.fathom_synonyms,
          this.params.fathom_hypernyms, this.params.fathom_hyponyms,
          this.params.fathom_meronyms, this.params.fathom_holonyms);
      break;
    case LESK_EXT_CTXT_WIN:
      wsenses = extendedContextual(sample, this.params.win_b_size,
          this.params.depth, this.params.depth_weight,
          this.params.fathom_synonyms, this.params.fathom_hypernyms,
          this.params.fathom_hyponyms, this.params.fathom_meronyms,
          this.params.fathom_holonyms);
      break;
    case LESK_EXT_CTXT_WIN_BF:
      wsenses = extendedContextual(sample, this.params.win_b_size,
          this.params.win_f_size, this.params.depth, this.params.depth_weight,
          this.params.fathom_synonyms, this.params.fathom_hypernyms,
          this.params.fathom_hyponyms, this.params.fathom_meronyms,
          this.params.fathom_holonyms);
      break;
    case LESK_EXT_EXP:
      wsenses = extendedExponential(sample, this.params.depth,
          this.params.iexp, this.params.dexp, this.params.fathom_synonyms,
          this.params.fathom_hypernyms, this.params.fathom_hyponyms,
          this.params.fathom_meronyms, this.params.fathom_holonyms);
      break;
    case LESK_EXT_EXP_CTXT:
      wsenses = extendedExponentialContextual(sample, this.params.depth,
          this.params.iexp, this.params.dexp, this.params.fathom_synonyms,
          this.params.fathom_hypernyms, this.params.fathom_hyponyms,
          this.params.fathom_meronyms, this.params.fathom_holonyms);
      break;
    case LESK_EXT_EXP_CTXT_WIN:
      wsenses = extendedExponentialContextual(sample, this.params.win_b_size,
          this.params.depth, this.params.iexp, this.params.dexp,
          this.params.fathom_synonyms, this.params.fathom_hypernyms,
          this.params.fathom_hyponyms, this.params.fathom_meronyms,
          this.params.fathom_holonyms);
      break;
    case LESK_EXT_EXP_CTXT_WIN_BF:
      wsenses = extendedExponentialContextual(sample, this.params.win_b_size,
          this.params.win_f_size, this.params.depth, this.params.iexp,
          this.params.dexp, this.params.fathom_synonyms,
          this.params.fathom_hypernyms, this.params.fathom_hyponyms,
          this.params.fathom_meronyms, this.params.fathom_holonyms);
      break;
    default:
      wsenses = extendedExponentialContextual(sample,
          LeskParameters.DFLT_WIN_SIZE, LeskParameters.DFLT_DEPTH,
          LeskParameters.DFLT_IEXP, LeskParameters.DFLT_DEXP, true, true, true,
          true, true);
      break;
    }

    Collections.sort(wsenses);

    String[] senses;
    if (wsenses.get(0).getScore() > 0) { // if at least one overlap
      List<Word> synsetWords;
      senses = new String[wsenses.size()];
      String senseKey = "?";
      for (int i = 0; i < wsenses.size(); i++) {
        synsetWords = wsenses.get(i).getNode().synset.getWords();
        for (Word synWord : synsetWords) {
          if (synWord.getLemma().equals(sample.getTargetLemma())) {
            try {
              senseKey = synWord.getSenseKey();
            } catch (JWNLException e) {
              e.printStackTrace();
            }
            break;
          }
        }
        senses[i] = params.source.name() + " " + senseKey + " "
            + wsenses.get(i).getScore();

      }
    } else { // get the MFS if no overlaps
      senses = new String[1];
      senses[0] = MFS.getMostFrequentSense(sample) + " -1";
    }
    return senses;
  }

  @Override
  public String[] disambiguate(String[] inputText, int inputWordIndex) {
    // TODO Deprecate
    return null;
  }

}
