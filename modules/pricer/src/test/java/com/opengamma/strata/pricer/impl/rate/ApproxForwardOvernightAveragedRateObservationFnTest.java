/**
 * Copyright (C) 2015 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.strata.pricer.impl.rate;

import static com.opengamma.strata.basics.date.DayCounts.ACT_ACT_ISDA;
import static com.opengamma.strata.basics.index.OvernightIndices.GBP_SONIA;
import static com.opengamma.strata.basics.index.OvernightIndices.USD_FED_FUND;
import static com.opengamma.strata.collect.TestHelper.assertThrows;
import static com.opengamma.strata.collect.TestHelper.date;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.time.LocalDate;
import java.util.Arrays;

import org.testng.annotations.Test;

import com.google.common.collect.ImmutableMap;
import com.opengamma.strata.basics.currency.CurrencyAmount;
import com.opengamma.strata.basics.index.OvernightIndex;
import com.opengamma.strata.basics.interpolator.CurveInterpolator;
import com.opengamma.strata.collect.array.DoubleArray;
import com.opengamma.strata.collect.timeseries.LocalDateDoubleTimeSeries;
import com.opengamma.strata.collect.timeseries.LocalDateDoubleTimeSeriesBuilder;
import com.opengamma.strata.finance.rate.OvernightAveragedRateObservation;
import com.opengamma.strata.market.curve.Curve;
import com.opengamma.strata.market.curve.Curves;
import com.opengamma.strata.market.curve.InterpolatedNodalCurve;
import com.opengamma.strata.market.explain.ExplainKey;
import com.opengamma.strata.market.explain.ExplainMap;
import com.opengamma.strata.market.explain.ExplainMapBuilder;
import com.opengamma.strata.market.interpolator.CurveInterpolators;
import com.opengamma.strata.market.sensitivity.CurveCurrencyParameterSensitivities;
import com.opengamma.strata.market.sensitivity.OvernightRateSensitivity;
import com.opengamma.strata.market.sensitivity.PointSensitivityBuilder;
import com.opengamma.strata.market.value.OvernightIndexRates;
import com.opengamma.strata.pricer.PricingException;
import com.opengamma.strata.pricer.rate.ImmutableRatesProvider;
import com.opengamma.strata.pricer.rate.SimpleRatesProvider;
import com.opengamma.strata.pricer.sensitivity.RatesFiniteDifferenceSensitivityCalculator;

/**
 * Test {@link ApproxForwardOvernightAveragedRateObservationFn}.
 */
@Test
public class ApproxForwardOvernightAveragedRateObservationFnTest {

  private static final LocalDate DUMMY_ACCRUAL_START_DATE = date(2015, 1, 1); // Accrual dates irrelevant for the rate
  private static final LocalDate DUMMY_ACCRUAL_END_DATE = date(2015, 1, 1); // Accrual dates irrelevant for the rate
  private static final LocalDate FIXING_START_DATE = date(2015, 1, 8);
  private static final LocalDate FIXING_END_DATE = date(2015, 1, 15); // 1w only to decrease data
  private static final LocalDate[] FIXING_DATES = new LocalDate[] {
      date(2015, 1, 7), date(2015, 1, 8), date(2015, 1, 9),
      date(2015, 1, 12), date(2015, 1, 13), date(2015, 1, 14), date(2015, 1, 15)};
  private static final double[] FIXING_RATES = {
      0.0012, 0.0023, 0.0034,
      0.0045, 0.0056, 0.0067, 0.0078};
  private static final double[] FORWARD_RATES = {
      0.0112, 0.0123, 0.0134,
      0.0145, 0.0156, 0.0167, 0.0178};

  private static final double TOLERANCE_RATE = 1.0E-10;
  private static final double TOLERANCE_APPROX = 1.0E-6;
  private static final double EPS_FD = 1.0E-7;

  private static final ApproxForwardOvernightAveragedRateObservationFn OBS_FN_APPROX_FWD =
      ApproxForwardOvernightAveragedRateObservationFn.DEFAULT;
  private static final ForwardOvernightAveragedRateObservationFn OBS_FN_DET_FWD =
      ForwardOvernightAveragedRateObservationFn.DEFAULT;

  //-------------------------------------------------------------------------
  /** Compare the rate estimated with approximation to the rate estimated by daily forward. */
  public void comparisonApproxVNoApprox() {
    LocalDate valuationDate = date(2015, 1, 5);
    OvernightAveragedRateObservation ro =
        OvernightAveragedRateObservation.of(USD_FED_FUND, FIXING_START_DATE, FIXING_END_DATE, 0);
    OvernightIndexRates mockRates = mock(OvernightIndexRates.class);
    when(mockRates.getIndex()).thenReturn(USD_FED_FUND);
    when(mockRates.getValuationDate()).thenReturn(valuationDate);
    SimpleRatesProvider simpleProv = new SimpleRatesProvider(valuationDate, mockRates);

    for (int i = 0; i < FIXING_DATES.length; i++) {
      when(mockRates.rate(FIXING_DATES[i])).thenReturn(FORWARD_RATES[i]);
    }
    double investmentFactor = 1.0;
    double totalAf = 0.0;
    for (int i = 1; i < 6; i++) {
      LocalDate endDate = USD_FED_FUND.calculateMaturityFromEffective(FIXING_DATES[i]);
      double af = USD_FED_FUND.getDayCount().yearFraction(FIXING_DATES[i], endDate);
      totalAf += af;
      investmentFactor *= 1.0d + af * FORWARD_RATES[i];
    }
    double rateCmp = (investmentFactor - 1.0d) / totalAf;
    when(mockRates.periodRate(FIXING_START_DATE, FIXING_END_DATE)).thenReturn(rateCmp);
    double rateApprox = OBS_FN_APPROX_FWD.rate(ro, DUMMY_ACCRUAL_START_DATE, DUMMY_ACCRUAL_END_DATE, simpleProv);
    double rateDet = OBS_FN_DET_FWD.rate(ro, DUMMY_ACCRUAL_START_DATE, DUMMY_ACCRUAL_END_DATE, simpleProv);
    assertEquals(rateDet, rateApprox, TOLERANCE_APPROX);

    // explain
    ExplainMapBuilder builder = ExplainMap.builder();
    double explainedRate = OBS_FN_APPROX_FWD.explainRate(
        ro, DUMMY_ACCRUAL_START_DATE, DUMMY_ACCRUAL_END_DATE, simpleProv, builder);
    assertEquals(explainedRate, rateApprox, TOLERANCE_APPROX);

    ExplainMap built = builder.build();
    assertEquals(built.get(ExplainKey.OBSERVATIONS).isPresent(), false);
    assertEquals(built.get(ExplainKey.COMBINED_RATE).get().doubleValue(), rateApprox, TOLERANCE_APPROX);
  }

  /** No cutoff period and the period entirely forward. Test the approximation part only. */
  public void rateFedFundNoCutOffForward() { // publication=1, cutoff=0, effective offset=0, Forward
    LocalDate[] valuationDate = {date(2015, 1, 1), date(2015, 1, 8)};
    OvernightAveragedRateObservation ro =
        OvernightAveragedRateObservation.of(USD_FED_FUND, FIXING_START_DATE, FIXING_END_DATE, 0);
    OvernightIndexRates mockRates = mock(OvernightIndexRates.class);
    when(mockRates.getIndex()).thenReturn(USD_FED_FUND);
    SimpleRatesProvider simpleProv = new SimpleRatesProvider(mockRates);

    for (int i = 0; i < FIXING_DATES.length; i++) {
      when(mockRates.rate(FIXING_DATES[i])).thenReturn(FORWARD_RATES[i]);
    }
    double investmentFactor = 1.0;
    double totalAf = 0.0;
    for (int i = 1; i < 6; i++) {
      LocalDate endDate = USD_FED_FUND.calculateMaturityFromEffective(FIXING_DATES[i]);
      double af = USD_FED_FUND.getDayCount().yearFraction(FIXING_DATES[i], endDate);
      totalAf += af;
      investmentFactor *= 1.0d + af * FORWARD_RATES[i];
    }
    double rateCmp = (investmentFactor - 1.0d) / totalAf;
    when(mockRates.periodRate(FIXING_START_DATE, FIXING_END_DATE)).thenReturn(rateCmp);
    double rateExpected = Math.log(1.0 + rateCmp * totalAf) / totalAf;
    for (int loopvaldate = 0; loopvaldate < 2; loopvaldate++) {
      when(mockRates.getValuationDate()).thenReturn(valuationDate[loopvaldate]);
      double rateComputed = OBS_FN_APPROX_FWD.rate(ro, DUMMY_ACCRUAL_START_DATE, DUMMY_ACCRUAL_END_DATE, simpleProv);
      assertEquals(rateComputed, rateExpected, TOLERANCE_RATE);
    }
  }

  /** Test rate sensitivity against FD approximation. No cutoff period and the period entirely forward. */
  public void rateFedFundNoCutOffForwardSensitivity() { // publication=1, cutoff=0, effective offset=0, Forward
    LocalDate[] valuationDate = {date(2015, 1, 1), date(2015, 1, 8)};
    OvernightAveragedRateObservation ro =
        OvernightAveragedRateObservation.of(USD_FED_FUND, FIXING_START_DATE, FIXING_END_DATE, 0);
    OvernightIndexRates mockRates = mock(OvernightIndexRates.class);
    when(mockRates.getIndex()).thenReturn(USD_FED_FUND);
    SimpleRatesProvider simpleProv = new SimpleRatesProvider(mockRates);

    OvernightIndexRates mockRatesUp = mock(OvernightIndexRates.class);
    SimpleRatesProvider simpleProvUp = new SimpleRatesProvider(mockRatesUp);
    OvernightIndexRates mockRatesDw = mock(OvernightIndexRates.class);
    SimpleRatesProvider simpleProvDw = new SimpleRatesProvider(mockRatesDw);
    double investmentFactor = 1.0;
    double totalAf = 0.0;
    for (int i = 1; i < 6; i++) {
      LocalDate endDate = USD_FED_FUND.calculateMaturityFromEffective(FIXING_DATES[i]);
      double af = USD_FED_FUND.getDayCount().yearFraction(FIXING_DATES[i], endDate);
      totalAf += af;
      investmentFactor *= 1.0d + af * FORWARD_RATES[i];
    }
    double rateCmp = (investmentFactor - 1.0d) / totalAf;
    when(mockRates.periodRate(FIXING_START_DATE, FIXING_END_DATE)).thenReturn(rateCmp);
    when(mockRatesUp.periodRate(FIXING_START_DATE, FIXING_END_DATE)).thenReturn(
        rateCmp + EPS_FD);
    when(mockRatesDw.periodRate(FIXING_START_DATE, FIXING_END_DATE)).thenReturn(
        rateCmp - EPS_FD);
    PointSensitivityBuilder periodSensitivity = OvernightRateSensitivity.of(USD_FED_FUND, FIXING_START_DATE,
        FIXING_END_DATE, USD_FED_FUND.getCurrency(), 1d);
    when(mockRates.periodRatePointSensitivity(FIXING_START_DATE, FIXING_END_DATE))
        .thenReturn(periodSensitivity);
    for (int loopvaldate = 0; loopvaldate < 2; loopvaldate++) {
      when(mockRates.getValuationDate()).thenReturn(valuationDate[loopvaldate]);
      when(mockRatesUp.getValuationDate()).thenReturn(valuationDate[loopvaldate]);
      when(mockRatesDw.getValuationDate()).thenReturn(valuationDate[loopvaldate]);
      double rateUp = OBS_FN_APPROX_FWD.rate(ro, DUMMY_ACCRUAL_START_DATE, DUMMY_ACCRUAL_END_DATE, simpleProvUp);
      double rateDw = OBS_FN_APPROX_FWD.rate(ro, DUMMY_ACCRUAL_START_DATE, DUMMY_ACCRUAL_END_DATE, simpleProvDw);
      double sensitivityExpected = 0.5 * (rateUp - rateDw) / EPS_FD;
      PointSensitivityBuilder sensitivityBuilderExpected = OvernightRateSensitivity.of(USD_FED_FUND,
          FIXING_START_DATE, FIXING_END_DATE, USD_FED_FUND.getCurrency(), sensitivityExpected);
      PointSensitivityBuilder sensitivityBuilderComputed = OBS_FN_APPROX_FWD.rateSensitivity(ro,
          DUMMY_ACCRUAL_START_DATE, DUMMY_ACCRUAL_END_DATE, simpleProv);
      assertTrue(sensitivityBuilderComputed.build().normalized().equalWithTolerance(
          sensitivityBuilderExpected.build().normalized(), EPS_FD));
    }
  }

  /** Two days cutoff and the period is entirely forward. Test Approximation part plus cutoff specifics.*/
  public void rateFedFund2CutOffForward() { // publication=1, cutoff=2, effective offset=0, Forward
    LocalDate[] valuationDate = {date(2015, 1, 1), date(2015, 1, 8)};
    OvernightAveragedRateObservation ro =
        OvernightAveragedRateObservation.of(USD_FED_FUND, FIXING_START_DATE, FIXING_END_DATE, 2);
    OvernightIndexRates mockRates = mock(OvernightIndexRates.class);
    when(mockRates.getIndex()).thenReturn(USD_FED_FUND);
    SimpleRatesProvider simpleProv = new SimpleRatesProvider(mockRates);

    for (int i = 0; i < FIXING_DATES.length; i++) {
      when(mockRates.rate(FIXING_DATES[i])).thenReturn(FORWARD_RATES[i]);
    }
    double investmentFactor = 1.0;
    double afApprox = 0.0;
    for (int i = 1; i < 5; i++) {
      LocalDate endDate = USD_FED_FUND.calculateMaturityFromEffective(FIXING_DATES[i]);
      double af = USD_FED_FUND.getDayCount().yearFraction(FIXING_DATES[i], endDate);
      afApprox += af;
      investmentFactor *= 1.0d + af * FORWARD_RATES[i];
    }
    double rateCmp = (investmentFactor - 1.0d) / afApprox;
    when(mockRates.periodRate(FIXING_START_DATE, USD_FED_FUND.getFixingCalendar().previous(FIXING_END_DATE)))
        .thenReturn(rateCmp);
    LocalDate fixingCutOff = FIXING_DATES[5];
    LocalDate endDate = USD_FED_FUND.calculateMaturityFromEffective(fixingCutOff);
    double afCutOff = USD_FED_FUND.getDayCount().yearFraction(fixingCutOff, endDate);
    double rateExpected = (Math.log(1.0 + rateCmp * afApprox) + FORWARD_RATES[4] * afCutOff) / (afApprox + afCutOff);
    for (int loopvaldate = 0; loopvaldate < 2; loopvaldate++) {
      when(mockRates.getValuationDate()).thenReturn(valuationDate[loopvaldate]);
      double rateComputed = OBS_FN_APPROX_FWD.rate(ro, DUMMY_ACCRUAL_START_DATE, DUMMY_ACCRUAL_END_DATE, simpleProv);
      assertEquals(rateComputed, rateExpected, TOLERANCE_RATE);
    }
  }

  /** Test rate sensitivity against FD approximation.  
   * Two days cutoff and the period is entirely forward. Test Approximation part plus cutoff specifics.*/
  public void rateFedFund2CutOffForwardSensitivity() { // publication=1, cutoff=2, effective offset=0, Forward
    LocalDate[] valuationDate = {date(2015, 1, 1), date(2015, 1, 8)};
    OvernightAveragedRateObservation ro =
        OvernightAveragedRateObservation.of(USD_FED_FUND, FIXING_START_DATE, FIXING_END_DATE, 2);
    OvernightIndexRates mockRates = mock(OvernightIndexRates.class);
    when(mockRates.getIndex()).thenReturn(USD_FED_FUND);
    SimpleRatesProvider simpleProv = new SimpleRatesProvider(mockRates);

    int nRates = FIXING_DATES.length;
    OvernightIndexRates[] mockRatesUp = new OvernightIndexRates[nRates];
    SimpleRatesProvider[] simpleProvUp = new SimpleRatesProvider[nRates];
    OvernightIndexRates[] mockRatesDw = new OvernightIndexRates[nRates];
    SimpleRatesProvider[] simpleProvDw = new SimpleRatesProvider[nRates];
    OvernightIndexRates mockRatesPeriodUp = mock(OvernightIndexRates.class);
    SimpleRatesProvider simpleProvPeriodUp = new SimpleRatesProvider(mockRatesPeriodUp);
    OvernightIndexRates mockRatesPeriodDw = mock(OvernightIndexRates.class);
    SimpleRatesProvider simpleProvPeriodDw = new SimpleRatesProvider(mockRatesPeriodDw);

    for (int i = 0; i < nRates; i++) {
      when(mockRates.rate(FIXING_DATES[i])).thenReturn(FORWARD_RATES[i]);
      LocalDate fixingStartDate = USD_FED_FUND.calculateEffectiveFromFixing(FIXING_DATES[i]);
      LocalDate fixingEndDate = USD_FED_FUND.calculateMaturityFromEffective(fixingStartDate);
      PointSensitivityBuilder pointSensitivity = OvernightRateSensitivity.of(USD_FED_FUND, FIXING_DATES[i],
          fixingEndDate, USD_FED_FUND.getCurrency(), 1d);
      when(mockRates.ratePointSensitivity(FIXING_DATES[i])).thenReturn(pointSensitivity);
    }
    double investmentFactor = 1.0;
    double afApprox = 0.0;
    for (int i = 1; i < 5; i++) {
      LocalDate endDate = USD_FED_FUND.calculateMaturityFromEffective(FIXING_DATES[i]);
      double af = USD_FED_FUND.getDayCount().yearFraction(FIXING_DATES[i], endDate);
      afApprox += af;
      investmentFactor *= 1.0d + af * FORWARD_RATES[i];
    }
    double rateCmp = (investmentFactor - 1.0d) / afApprox;
    when(mockRates.periodRate(FIXING_START_DATE, USD_FED_FUND.getFixingCalendar().previous(FIXING_END_DATE)))
        .thenReturn(rateCmp);
    PointSensitivityBuilder pointSensitivity = OvernightRateSensitivity.of(USD_FED_FUND, FIXING_START_DATE,
        USD_FED_FUND.getFixingCalendar().previous(FIXING_END_DATE), USD_FED_FUND.getCurrency(), 1d);
    when(mockRates.periodRatePointSensitivity(FIXING_START_DATE, USD_FED_FUND.getFixingCalendar().previous(FIXING_END_DATE)))
        .thenReturn(pointSensitivity);
    setRatesProviders(
        mockRatesUp, simpleProvUp, mockRatesDw, simpleProvDw,
        mockRatesPeriodUp, simpleProvPeriodUp, mockRatesPeriodDw, simpleProvPeriodDw,
        ro, USD_FED_FUND, FIXING_START_DATE, USD_FED_FUND.getFixingCalendar().previous(FIXING_END_DATE), rateCmp, null);
    for (int loopvaldate = 0; loopvaldate < 2; loopvaldate++) {
      when(mockRates.getValuationDate()).thenReturn(valuationDate[loopvaldate]);
      when(mockRatesPeriodUp.getValuationDate()).thenReturn(valuationDate[loopvaldate]);
      when(mockRatesPeriodDw.getValuationDate()).thenReturn(valuationDate[loopvaldate]);
      PointSensitivityBuilder sensitivityBuilderComputed = OBS_FN_APPROX_FWD.rateSensitivity(ro,
          DUMMY_ACCRUAL_START_DATE, DUMMY_ACCRUAL_END_DATE, simpleProv);

      PointSensitivityBuilder sensitivityBuilderExpected1 = PointSensitivityBuilder.none();
      for (int i = 0; i < nRates; ++i) {
        when(mockRatesUp[i].getValuationDate()).thenReturn(valuationDate[loopvaldate]);
        when(mockRatesDw[i].getValuationDate()).thenReturn(valuationDate[loopvaldate]);
        double rateUp = OBS_FN_APPROX_FWD.rate(ro, DUMMY_ACCRUAL_START_DATE, DUMMY_ACCRUAL_END_DATE, simpleProvUp[i]);
        double rateDw = OBS_FN_APPROX_FWD.rate(ro, DUMMY_ACCRUAL_START_DATE, DUMMY_ACCRUAL_END_DATE, simpleProvDw[i]);
        double res = 0.5 * (rateUp - rateDw) / EPS_FD;
        LocalDate fixingStartDate = USD_FED_FUND.calculateEffectiveFromFixing(FIXING_DATES[i]);
        LocalDate fixingEndDate = USD_FED_FUND.calculateMaturityFromEffective(fixingStartDate);
        sensitivityBuilderExpected1 = res == 0.0 ? sensitivityBuilderExpected1 : sensitivityBuilderExpected1
            .combinedWith(OvernightRateSensitivity.of(USD_FED_FUND, FIXING_DATES[i], fixingEndDate,
                USD_FED_FUND.getCurrency(), res));
      }
      double ratePeriodUp = OBS_FN_APPROX_FWD.rate(ro, DUMMY_ACCRUAL_START_DATE,
          DUMMY_ACCRUAL_END_DATE, simpleProvPeriodUp);
      double ratePeriodDw = OBS_FN_APPROX_FWD.rate(ro, DUMMY_ACCRUAL_START_DATE,
          DUMMY_ACCRUAL_END_DATE, simpleProvPeriodDw);
      double periodSensitivityExpected = 0.5 * (ratePeriodUp - ratePeriodDw) / EPS_FD;
      PointSensitivityBuilder sensitivityBuilderExpected2 = OvernightRateSensitivity.of(USD_FED_FUND,
          FIXING_START_DATE, USD_FED_FUND.getFixingCalendar().previous(FIXING_END_DATE), USD_FED_FUND.getCurrency(),
          periodSensitivityExpected);
      PointSensitivityBuilder sensitivityBuilderExpected = sensitivityBuilderExpected1
          .combinedWith(sensitivityBuilderExpected2);

      assertTrue(sensitivityBuilderComputed.build().normalized().equalWithTolerance(
          sensitivityBuilderExpected.build().normalized(), EPS_FD));
    }
  }

  private void setRatesProviders(
      OvernightIndexRates[] mockRatesUp,
      SimpleRatesProvider[] simpleProvUp,
      OvernightIndexRates[] mockRatesDw,
      SimpleRatesProvider[] simpleProvDw,
      OvernightIndexRates mockRatesPeriodUp,
      SimpleRatesProvider simpleProvPeriodUp,
      OvernightIndexRates mockRatesPeriodDw,
      SimpleRatesProvider simpleProvPeriodDw,
      OvernightAveragedRateObservation ro,
      OvernightIndex index,
      LocalDate periodStartDate,
      LocalDate PeriodEndDate,
      double rateCmp,
      LocalDateDoubleTimeSeriesBuilder tsb) {

    int nRates = FIXING_DATES.length;
    double[][] ratesUp = new double[nRates][];
    double[][] ratesDw = new double[nRates][];
    for (int i = 0; i < nRates; ++i) {
      mockRatesUp[i] = mock(OvernightIndexRates.class);
      simpleProvUp[i] = new SimpleRatesProvider(mockRatesUp[i]);
      mockRatesDw[i] = mock(OvernightIndexRates.class);
      simpleProvDw[i] = new SimpleRatesProvider(mockRatesDw[i]);
      ratesUp[i] = Arrays.copyOf(FIXING_RATES, nRates);
      ratesDw[i] = Arrays.copyOf(FIXING_RATES, nRates);
      ratesUp[i][i] += EPS_FD;
      ratesDw[i][i] -= EPS_FD;
    }
    for (int i = 0; i < nRates; i++) {
      when(mockRatesPeriodUp.rate(FIXING_DATES[i])).thenReturn(FORWARD_RATES[i]);
      when(mockRatesPeriodDw.rate(FIXING_DATES[i])).thenReturn(FORWARD_RATES[i]);
      when(mockRatesUp[i].periodRate(
          FIXING_START_DATE, index.getFixingCalendar().previous(FIXING_END_DATE))).thenReturn(rateCmp);
      when(mockRatesDw[i].periodRate(
          FIXING_START_DATE, index.getFixingCalendar().previous(FIXING_END_DATE))).thenReturn(rateCmp);
      for (int j = 0; j < nRates; ++j) {
        when(mockRatesUp[j].rate(FIXING_DATES[i])).thenReturn(ratesUp[j][i]);
        when(mockRatesDw[j].rate(FIXING_DATES[i])).thenReturn(ratesDw[j][i]);
      }
    }
    when(mockRatesPeriodUp.periodRate(periodStartDate, PeriodEndDate)).thenReturn(
        rateCmp + EPS_FD);
    when(mockRatesPeriodDw.periodRate(periodStartDate, PeriodEndDate)).thenReturn(
        rateCmp - EPS_FD);
    if (tsb != null) {
      when(mockRatesPeriodUp.getTimeSeries()).thenReturn(tsb.build());
      when(mockRatesPeriodDw.getTimeSeries()).thenReturn(tsb.build());
      for (int i = 0; i < nRates; i++) {
        when(mockRatesUp[i].getTimeSeries()).thenReturn(tsb.build());
        when(mockRatesDw[i].getTimeSeries()).thenReturn(tsb.build());
      }
    }
  }

  /** Two days cutoff and one already fixed ON rate. Test the already fixed portion with only one fixed ON rate.*/
  public void rateFedFund2CutOffValuation1() {
    // publication=1, cutoff=2, effective offset=0, TS: Fixing 1
    LocalDate[] valuationDate = {date(2015, 1, 9), date(2015, 1, 12)};
    OvernightAveragedRateObservation ro =
        OvernightAveragedRateObservation.of(USD_FED_FUND, FIXING_START_DATE, FIXING_END_DATE, 2);
    OvernightIndexRates mockRates = mock(OvernightIndexRates.class);
    when(mockRates.getIndex()).thenReturn(USD_FED_FUND);
    SimpleRatesProvider simpleProv = new SimpleRatesProvider(mockRates);

    LocalDateDoubleTimeSeriesBuilder tsb = LocalDateDoubleTimeSeries.builder();
    for (int i = 0; i < 2; i++) {
      tsb.put(FIXING_DATES[i], FIXING_RATES[i]);
    }
    when(mockRates.getTimeSeries()).thenReturn(tsb.build());
    for (int i = 0; i < 2; i++) {
      when(mockRates.rate(FIXING_DATES[i])).thenReturn(FIXING_RATES[i]);
    }
    for (int i = 2; i < FIXING_DATES.length; i++) {
      when(mockRates.rate(FIXING_DATES[i])).thenReturn(FORWARD_RATES[i]);
    }
    LocalDate fixingknown = FIXING_DATES[1];
    LocalDate endDateKnown = USD_FED_FUND.calculateMaturityFromEffective(fixingknown);
    double afKnown = USD_FED_FUND.getDayCount().yearFraction(fixingknown, endDateKnown);
    double investmentFactor = 1.0;
    double afApprox = 0.0;
    for (int i = 2; i < 5; i++) {
      LocalDate endDate = USD_FED_FUND.calculateMaturityFromEffective(FIXING_DATES[i]);
      double af = USD_FED_FUND.getDayCount().yearFraction(FIXING_DATES[i], endDate);
      afApprox += af;
      investmentFactor *= 1.0d + af * FORWARD_RATES[i];
    }
    double rateCmp = (investmentFactor - 1.0d) / afApprox;
    when(mockRates.periodRate(
        USD_FED_FUND.getFixingCalendar().next(FIXING_START_DATE),
        USD_FED_FUND.getFixingCalendar().previous(FIXING_END_DATE)))
        .thenReturn(rateCmp);
    LocalDate fixingCutOff = FIXING_DATES[5];
    LocalDate endDateCutOff = USD_FED_FUND.calculateMaturityFromEffective(fixingCutOff);
    double afCutOff = USD_FED_FUND.getDayCount().yearFraction(fixingCutOff, endDateCutOff);
    double rateExpected = (FIXING_RATES[1] * afKnown + Math.log(1.0 + rateCmp * afApprox) + FORWARD_RATES[4] * afCutOff)
        / (afKnown + afApprox + afCutOff);
    for (int loopvaldate = 0; loopvaldate < 2; loopvaldate++) {
      when(mockRates.getValuationDate()).thenReturn(valuationDate[loopvaldate]);
      double rateComputed = OBS_FN_APPROX_FWD.rate(ro, DUMMY_ACCRUAL_START_DATE, DUMMY_ACCRUAL_END_DATE, simpleProv);
      assertEquals(rateComputed, rateExpected, TOLERANCE_RATE);
    }
  }

  /** Test rate sensitivity against FD approximation.  
   * Two days cutoff and one already fixed ON rate. Test the already fixed portion with only one fixed ON rate.*/
  public void rateFedFund2CutOffValuation1Sensitivity() {
    // publication=1, cutoff=2, effective offset=0, TS: Fixing 1
    LocalDate[] valuationDate = {date(2015, 1, 9), date(2015, 1, 12)};
    OvernightAveragedRateObservation ro =
        OvernightAveragedRateObservation.of(USD_FED_FUND, FIXING_START_DATE, FIXING_END_DATE, 2);
    OvernightIndexRates mockRates = mock(OvernightIndexRates.class);
    when(mockRates.getIndex()).thenReturn(USD_FED_FUND);
    SimpleRatesProvider simpleProv = new SimpleRatesProvider(mockRates);

    LocalDateDoubleTimeSeriesBuilder tsb = LocalDateDoubleTimeSeries.builder();
    for (int i = 0; i < 2; i++) {
      tsb.put(FIXING_DATES[i], FIXING_RATES[i]);
    }
    int nRates = FIXING_DATES.length;
    OvernightIndexRates[] mockRatesUp = new OvernightIndexRates[nRates];
    SimpleRatesProvider[] simpleProvUp = new SimpleRatesProvider[nRates];
    OvernightIndexRates[] mockRatesDw = new OvernightIndexRates[nRates];
    SimpleRatesProvider[] simpleProvDw = new SimpleRatesProvider[nRates];
    OvernightIndexRates mockRatesPeriodUp = mock(OvernightIndexRates.class);
    SimpleRatesProvider simpleProvPeriodUp = new SimpleRatesProvider(mockRatesPeriodUp);
    OvernightIndexRates mockRatesPeriodDw = mock(OvernightIndexRates.class);
    SimpleRatesProvider simpleProvPeriodDw = new SimpleRatesProvider(mockRatesPeriodDw);

    when(mockRates.getTimeSeries()).thenReturn(tsb.build());
    for (int i = 0; i < 2; i++) {
      when(mockRates.rate(FIXING_DATES[i])).thenReturn(FIXING_RATES[i]);
      when(mockRates.ratePointSensitivity(FIXING_DATES[i])).thenReturn(PointSensitivityBuilder.none());
    }
    for (int i = 2; i < nRates; i++) {
      when(mockRates.rate(FIXING_DATES[i])).thenReturn(FORWARD_RATES[i]);
      LocalDate fixingStartDate = USD_FED_FUND.calculateEffectiveFromFixing(FIXING_DATES[i]);
      LocalDate fixingEndDate = USD_FED_FUND.calculateMaturityFromEffective(fixingStartDate);
      PointSensitivityBuilder pointSensitivity = OvernightRateSensitivity.of(USD_FED_FUND, FIXING_DATES[i],
          fixingEndDate, USD_FED_FUND.getCurrency(), 1d);
      when(mockRates.ratePointSensitivity(FIXING_DATES[i])).thenReturn(pointSensitivity);
    }
    double investmentFactor = 1.0;
    double afApprox = 0.0;
    for (int i = 2; i < 5; i++) {
      LocalDate endDate = USD_FED_FUND.calculateMaturityFromEffective(FIXING_DATES[i]);
      double af = USD_FED_FUND.getDayCount().yearFraction(FIXING_DATES[i], endDate);
      afApprox += af;
      investmentFactor *= 1.0d + af * FORWARD_RATES[i];
    }
    double rateCmp = (investmentFactor - 1.0d) / afApprox;
    when(mockRates.periodRate(
        USD_FED_FUND.getFixingCalendar().next(FIXING_START_DATE),
        USD_FED_FUND.getFixingCalendar().previous(FIXING_END_DATE))).thenReturn(rateCmp);
    PointSensitivityBuilder periodSensitivity = OvernightRateSensitivity.of(USD_FED_FUND, USD_FED_FUND.getFixingCalendar().next(FIXING_START_DATE),
        USD_FED_FUND.getFixingCalendar().previous(FIXING_END_DATE),
        USD_FED_FUND.getCurrency(), 1d);
    when(mockRates.periodRatePointSensitivity(
        USD_FED_FUND.getFixingCalendar().next(FIXING_START_DATE),
        USD_FED_FUND.getFixingCalendar().previous(FIXING_END_DATE))).thenReturn(periodSensitivity);
    setRatesProviders(
        mockRatesUp, simpleProvUp, mockRatesDw, simpleProvDw,
        mockRatesPeriodUp, simpleProvPeriodUp, mockRatesPeriodDw, simpleProvPeriodDw,
        ro, USD_FED_FUND, USD_FED_FUND.getFixingCalendar().next(FIXING_START_DATE),
        USD_FED_FUND.getFixingCalendar().previous(FIXING_END_DATE),
        rateCmp, tsb);
    for (int i = 0; i < 2; i++) {
      when(mockRatesPeriodUp.rate(FIXING_DATES[i])).thenReturn(FIXING_RATES[i]);
      when(mockRatesPeriodDw.rate(FIXING_DATES[i])).thenReturn(FIXING_RATES[i]);
      for (int j = 0; j < nRates; ++j) {
        when(mockRatesUp[j].rate(FIXING_DATES[i])).thenReturn(FIXING_RATES[i]);
        when(mockRatesDw[j].rate(FIXING_DATES[i])).thenReturn(FIXING_RATES[i]);
      }
    }
    for (int loopvaldate = 0; loopvaldate < 2; loopvaldate++) {
      when(mockRates.getValuationDate()).thenReturn(valuationDate[loopvaldate]);
      when(mockRatesPeriodUp.getValuationDate()).thenReturn(valuationDate[loopvaldate]);
      when(mockRatesPeriodDw.getValuationDate()).thenReturn(valuationDate[loopvaldate]);
      PointSensitivityBuilder sensitivityBuilderComputed = OBS_FN_APPROX_FWD.rateSensitivity(ro,
          DUMMY_ACCRUAL_START_DATE, DUMMY_ACCRUAL_END_DATE, simpleProv);

      PointSensitivityBuilder sensitivityBuilderExpected1 = PointSensitivityBuilder.none();
      for (int i = 0; i < nRates; ++i) {
        when(mockRatesUp[i].getValuationDate()).thenReturn(valuationDate[loopvaldate]);
        when(mockRatesDw[i].getValuationDate()).thenReturn(valuationDate[loopvaldate]);
        double rateUp = OBS_FN_APPROX_FWD.rate(ro, DUMMY_ACCRUAL_START_DATE, DUMMY_ACCRUAL_END_DATE, simpleProvUp[i]);
        double rateDw = OBS_FN_APPROX_FWD.rate(ro, DUMMY_ACCRUAL_START_DATE, DUMMY_ACCRUAL_END_DATE, simpleProvDw[i]);
        double res = 0.5 * (rateUp - rateDw) / EPS_FD;
        LocalDate fixingStartDate = USD_FED_FUND.calculateEffectiveFromFixing(FIXING_DATES[i]);
        LocalDate fixingEndDate = USD_FED_FUND.calculateMaturityFromEffective(fixingStartDate);
        sensitivityBuilderExpected1 = res == 0.0 ? sensitivityBuilderExpected1 : sensitivityBuilderExpected1
            .combinedWith(OvernightRateSensitivity.of(USD_FED_FUND, FIXING_DATES[i], fixingEndDate,
                USD_FED_FUND.getCurrency(), res));
      }
      double ratePeriodUp = OBS_FN_APPROX_FWD.rate(ro, DUMMY_ACCRUAL_START_DATE, DUMMY_ACCRUAL_END_DATE,
          simpleProvPeriodUp);
      double ratePeriodDw = OBS_FN_APPROX_FWD.rate(ro, DUMMY_ACCRUAL_START_DATE, DUMMY_ACCRUAL_END_DATE,
          simpleProvPeriodDw);
      double periodSensitivityExpected = 0.5 * (ratePeriodUp - ratePeriodDw) / EPS_FD;
      PointSensitivityBuilder sensitivityBuilderExpected2 = OvernightRateSensitivity.of(USD_FED_FUND,
          USD_FED_FUND.getFixingCalendar().next(FIXING_START_DATE), USD_FED_FUND.getFixingCalendar().previous(FIXING_END_DATE),
          USD_FED_FUND.getCurrency(), periodSensitivityExpected);
      PointSensitivityBuilder sensitivityBuilderExpected = sensitivityBuilderExpected1
          .combinedWith(sensitivityBuilderExpected2);

      assertTrue(sensitivityBuilderComputed.build().normalized().equalWithTolerance(
          sensitivityBuilderExpected.build().normalized(), EPS_FD));
    }
  }

  /** Two days cutoff and two already fixed ON rate. ON index is Fed Fund. */
  public void rateFedFund2CutOffValuation2() {
    // publication=1, cutoff=2, effective offset=0, TS: Fixing 2
    LocalDate[] valuationDate = {date(2015, 1, 12), date(2015, 1, 13)};
    OvernightAveragedRateObservation ro =
        OvernightAveragedRateObservation.of(USD_FED_FUND, FIXING_START_DATE, FIXING_END_DATE, 2);
    OvernightIndexRates mockRates = mock(OvernightIndexRates.class);
    when(mockRates.getIndex()).thenReturn(USD_FED_FUND);
    SimpleRatesProvider simpleProv = new SimpleRatesProvider(mockRates);

    LocalDateDoubleTimeSeriesBuilder tsb = LocalDateDoubleTimeSeries.builder();
    int lastFixing = 3;
    for (int i = 0; i < lastFixing; i++) {
      tsb.put(FIXING_DATES[i], FIXING_RATES[i]);
    }
    when(mockRates.getTimeSeries()).thenReturn(tsb.build());
    for (int i = 0; i < lastFixing; i++) {
      when(mockRates.rate(FIXING_DATES[i])).thenReturn(FIXING_RATES[i]);
    }
    for (int i = lastFixing; i < FIXING_DATES.length; i++) {
      when(mockRates.rate(FIXING_DATES[i])).thenReturn(FORWARD_RATES[i]);
    }
    double afKnown = 0.0;
    double accruedKnown = 0.0;
    for (int i = 0; i < lastFixing - 1; i++) {
      LocalDate fixingknown = FIXING_DATES[i + 1];
      LocalDate endDateKnown = USD_FED_FUND.calculateMaturityFromEffective(fixingknown);
      double af = USD_FED_FUND.getDayCount().yearFraction(fixingknown, endDateKnown);
      afKnown += af;
      accruedKnown += FIXING_RATES[i + 1] * af;
    }
    double investmentFactor = 1.0;
    double afApprox = 0.0;
    for (int i = lastFixing; i < 5; i++) {
      LocalDate endDate = USD_FED_FUND.calculateMaturityFromEffective(FIXING_DATES[i]);
      double af = USD_FED_FUND.getDayCount().yearFraction(FIXING_DATES[i], endDate);
      afApprox += af;
      investmentFactor *= 1.0d + af * FORWARD_RATES[i];
    }
    double rateCmp = (investmentFactor - 1.0d) / afApprox;
    when(mockRates.periodRate(FIXING_DATES[lastFixing], FIXING_DATES[5])).thenReturn(rateCmp);
    LocalDate fixingCutOff = FIXING_DATES[5];
    LocalDate endDateCutOff = USD_FED_FUND.calculateMaturityFromEffective(fixingCutOff);
    double afCutOff = USD_FED_FUND.getDayCount().yearFraction(fixingCutOff, endDateCutOff);
    double rateExpected = (accruedKnown + Math.log(1.0 + rateCmp * afApprox) + FORWARD_RATES[4] * afCutOff)
        / (afKnown + afApprox + afCutOff);
    for (int loopvaldate = 0; loopvaldate < 2; loopvaldate++) {
      when(mockRates.getValuationDate()).thenReturn(valuationDate[loopvaldate]);
      double rateComputed = OBS_FN_APPROX_FWD.rate(ro, DUMMY_ACCRUAL_START_DATE, DUMMY_ACCRUAL_END_DATE, simpleProv);
      assertEquals(rateComputed, rateExpected, TOLERANCE_RATE);
    }
  }

  /** Test rate sensitivity against FD approximation.  
   * Two days cutoff and two already fixed ON rate. ON index is Fed Fund. */
  public void rateFedFund2CutOffValuation2Sensitivity() {
    // publication=1, cutoff=2, effective offset=0, TS: Fixing 2
    LocalDate[] valuationDate = {date(2015, 1, 12), date(2015, 1, 13)};
    OvernightAveragedRateObservation ro =
        OvernightAveragedRateObservation.of(USD_FED_FUND, FIXING_START_DATE, FIXING_END_DATE, 2);
    OvernightIndexRates mockRates = mock(OvernightIndexRates.class);
    when(mockRates.getIndex()).thenReturn(USD_FED_FUND);
    SimpleRatesProvider simpleProv = new SimpleRatesProvider(mockRates);

    LocalDateDoubleTimeSeriesBuilder tsb = LocalDateDoubleTimeSeries.builder();
    int lastFixing = 3;
    for (int i = 0; i < lastFixing; i++) {
      tsb.put(FIXING_DATES[i], FIXING_RATES[i]);
    }
    int nRates = FIXING_DATES.length;
    OvernightIndexRates[] mockRatesUp = new OvernightIndexRates[nRates];
    SimpleRatesProvider[] simpleProvUp = new SimpleRatesProvider[nRates];
    OvernightIndexRates[] mockRatesDw = new OvernightIndexRates[nRates];
    SimpleRatesProvider[] simpleProvDw = new SimpleRatesProvider[nRates];
    OvernightIndexRates mockRatesPeriodUp = mock(OvernightIndexRates.class);
    SimpleRatesProvider simpleProvPeriodUp = new SimpleRatesProvider(mockRatesPeriodUp);
    OvernightIndexRates mockRatesPeriodDw = mock(OvernightIndexRates.class);
    SimpleRatesProvider simpleProvPeriodDw = new SimpleRatesProvider(mockRatesPeriodDw);

    when(mockRates.getTimeSeries()).thenReturn(tsb.build());
    for (int i = 0; i < lastFixing; i++) {
      when(mockRates.rate(FIXING_DATES[i])).thenReturn(FIXING_RATES[i]);
      when(mockRates.ratePointSensitivity(FIXING_DATES[i])).thenReturn(
          PointSensitivityBuilder.none());
    }
    for (int i = lastFixing; i < FIXING_DATES.length; i++) {
      when(mockRates.rate(FIXING_DATES[i])).thenReturn(FORWARD_RATES[i]);
      LocalDate fixingStartDate = USD_FED_FUND.calculateEffectiveFromFixing(FIXING_DATES[i]);
      LocalDate fixingEndDate = USD_FED_FUND.calculateMaturityFromEffective(fixingStartDate);
      PointSensitivityBuilder pointSensitivity = OvernightRateSensitivity.of(USD_FED_FUND, FIXING_DATES[i],
          fixingEndDate, USD_FED_FUND.getCurrency(), 1d);
      when(mockRates.ratePointSensitivity(FIXING_DATES[i])).thenReturn(pointSensitivity);
    }
    double investmentFactor = 1.0;
    double afApprox = 0.0;
    for (int i = lastFixing; i < 5; i++) {
      LocalDate endDate = USD_FED_FUND.calculateMaturityFromEffective(FIXING_DATES[i]);
      double af = USD_FED_FUND.getDayCount().yearFraction(FIXING_DATES[i], endDate);
      afApprox += af;
      investmentFactor *= 1.0d + af * FORWARD_RATES[i];
    }
    double rateCmp = (investmentFactor - 1.0d) / afApprox;
    when(mockRates.periodRate(FIXING_DATES[lastFixing],
        FIXING_DATES[5])).thenReturn(rateCmp);
    PointSensitivityBuilder periodSensitivity = OvernightRateSensitivity.of(USD_FED_FUND, FIXING_DATES[lastFixing],
        FIXING_DATES[5], USD_FED_FUND.getCurrency(), 1d);
    when(mockRates.periodRatePointSensitivity(FIXING_DATES[lastFixing], FIXING_DATES[5]))
        .thenReturn(periodSensitivity);
    setRatesProviders(
        mockRatesUp, simpleProvUp, mockRatesDw, simpleProvDw,
        mockRatesPeriodUp, simpleProvPeriodUp, mockRatesPeriodDw, simpleProvPeriodDw,
        ro, USD_FED_FUND, FIXING_DATES[lastFixing], FIXING_DATES[5], rateCmp, tsb);
    when(mockRatesPeriodUp.getTimeSeries()).thenReturn(tsb.build());
    when(mockRatesPeriodDw.getTimeSeries()).thenReturn(tsb.build());
    for (int i = 0; i < lastFixing; i++) {
      when(mockRatesPeriodUp.rate(FIXING_DATES[i])).thenReturn(FIXING_RATES[i]);
      when(mockRatesPeriodDw.rate(FIXING_DATES[i])).thenReturn(FIXING_RATES[i]);
      for (int j = 0; j < nRates; ++j) {
        when(mockRatesUp[j].rate(FIXING_DATES[i])).thenReturn(FIXING_RATES[i]);
        when(mockRatesDw[j].rate(FIXING_DATES[i])).thenReturn(FIXING_RATES[i]);
      }
    }
    for (int loopvaldate = 0; loopvaldate < 2; loopvaldate++) {
      when(mockRates.getValuationDate()).thenReturn(valuationDate[loopvaldate]);
      when(mockRatesPeriodUp.getValuationDate()).thenReturn(valuationDate[loopvaldate]);
      when(mockRatesPeriodDw.getValuationDate()).thenReturn(valuationDate[loopvaldate]);
      PointSensitivityBuilder sensitivityBuilderComputed = OBS_FN_APPROX_FWD.rateSensitivity(ro,
          DUMMY_ACCRUAL_START_DATE, DUMMY_ACCRUAL_END_DATE, simpleProv);

      PointSensitivityBuilder sensitivityBuilderExpected1 = PointSensitivityBuilder.none();
      for (int i = 0; i < nRates; ++i) {
        when(mockRatesUp[i].getValuationDate()).thenReturn(valuationDate[loopvaldate]);
        when(mockRatesDw[i].getValuationDate()).thenReturn(valuationDate[loopvaldate]);
        double rateUp = OBS_FN_APPROX_FWD.rate(ro, DUMMY_ACCRUAL_START_DATE, DUMMY_ACCRUAL_END_DATE, simpleProvUp[i]);
        double rateDw = OBS_FN_APPROX_FWD.rate(ro, DUMMY_ACCRUAL_START_DATE, DUMMY_ACCRUAL_END_DATE, simpleProvDw[i]);
        double res = 0.5 * (rateUp - rateDw) / EPS_FD;
        LocalDate fixingStartDate = USD_FED_FUND.calculateEffectiveFromFixing(FIXING_DATES[i]);
        LocalDate fixingEndDate = USD_FED_FUND.calculateMaturityFromEffective(fixingStartDate);
        sensitivityBuilderExpected1 = res == 0.0 ? sensitivityBuilderExpected1 : sensitivityBuilderExpected1
            .combinedWith(OvernightRateSensitivity.of(USD_FED_FUND, FIXING_DATES[i], fixingEndDate,
                USD_FED_FUND.getCurrency(), res));
      }
      double ratePeriodUp = OBS_FN_APPROX_FWD.rate(ro, DUMMY_ACCRUAL_START_DATE, DUMMY_ACCRUAL_END_DATE,
          simpleProvPeriodUp);
      double ratePeriodDw = OBS_FN_APPROX_FWD.rate(ro, DUMMY_ACCRUAL_START_DATE, DUMMY_ACCRUAL_END_DATE,
          simpleProvPeriodDw);
      double periodSensitivityExpected = 0.5 * (ratePeriodUp - ratePeriodDw) / EPS_FD;
      PointSensitivityBuilder sensitivityBuilderExpected2 = OvernightRateSensitivity.of(USD_FED_FUND,
          FIXING_DATES[lastFixing], FIXING_DATES[5], USD_FED_FUND.getCurrency(), periodSensitivityExpected);
      PointSensitivityBuilder sensitivityBuilderExpected = sensitivityBuilderExpected1
          .combinedWith(sensitivityBuilderExpected2);

      assertTrue(sensitivityBuilderComputed.build().normalized().equalWithTolerance(
          sensitivityBuilderExpected.build().normalized(), EPS_FD));
    }
  }

  /** Two days cutoff and two already fixed ON rate. ON index is SONIA. */
  public void rateSonia2CutOffValuation2() {
    // publication=0, cutoff=2, effective offset=0, TS: Fixing 2
    LocalDate[] valuationDate = {date(2015, 1, 9), date(2015, 1, 12)};
    OvernightAveragedRateObservation ro =
        OvernightAveragedRateObservation.of(GBP_SONIA, FIXING_START_DATE, FIXING_END_DATE, 2);
    OvernightIndexRates mockRates = mock(OvernightIndexRates.class);
    when(mockRates.getIndex()).thenReturn(GBP_SONIA);
    SimpleRatesProvider simpleProv = new SimpleRatesProvider(mockRates);

    LocalDateDoubleTimeSeriesBuilder tsb = LocalDateDoubleTimeSeries.builder();
    int lastFixing = 3;
    for (int i = 0; i < lastFixing; i++) {
      tsb.put(FIXING_DATES[i], FIXING_RATES[i]);
    }
    when(mockRates.getTimeSeries()).thenReturn(tsb.build());
    for (int i = 0; i < lastFixing; i++) {
      when(mockRates.rate(FIXING_DATES[i])).thenReturn(FIXING_RATES[i]);
    }
    for (int i = lastFixing; i < FIXING_DATES.length; i++) {
      when(mockRates.rate(FIXING_DATES[i])).thenReturn(FORWARD_RATES[i]);
    }
    double afKnown = 0.0;
    double accruedKnown = 0.0;
    for (int i = 0; i < lastFixing - 1; i++) {
      LocalDate fixingknown = FIXING_DATES[i + 1];
      LocalDate endDateKnown = GBP_SONIA.calculateMaturityFromEffective(fixingknown);
      double af = GBP_SONIA.getDayCount().yearFraction(fixingknown, endDateKnown);
      afKnown += af;
      accruedKnown += FIXING_RATES[i + 1] * af;
    }
    double investmentFactor = 1.0;
    double afApprox = 0.0;
    for (int i = lastFixing; i < 5; i++) {
      LocalDate endDate = GBP_SONIA.calculateMaturityFromEffective(FIXING_DATES[i]);
      double af = GBP_SONIA.getDayCount().yearFraction(FIXING_DATES[i], endDate);
      afApprox += af;
      investmentFactor *= 1.0d + af * FORWARD_RATES[i];
    }
    double rateCmp = (investmentFactor - 1.0d) / afApprox;
    when(mockRates.periodRate(FIXING_DATES[lastFixing],
        FIXING_DATES[5])).thenReturn(rateCmp);
    LocalDate fixingCutOff = FIXING_DATES[5];
    LocalDate endDateCutOff = GBP_SONIA.calculateMaturityFromEffective(fixingCutOff);
    double afCutOff = GBP_SONIA.getDayCount().yearFraction(fixingCutOff, endDateCutOff);
    double rateExpected = (accruedKnown + Math.log(1.0 + rateCmp * afApprox) + FORWARD_RATES[4] * afCutOff)
        / (afKnown + afApprox + afCutOff);
    for (int loopvaldate = 0; loopvaldate < 2; loopvaldate++) {
      when(mockRates.getValuationDate()).thenReturn(valuationDate[loopvaldate]);
      double rateComputed = OBS_FN_APPROX_FWD.rate(ro, DUMMY_ACCRUAL_START_DATE, DUMMY_ACCRUAL_END_DATE, simpleProv);
      assertEquals(rateComputed, rateExpected, TOLERANCE_RATE);
    }
  }

  /** Test rate sensitivity against FD approximation.  
   * Two days cutoff and two already fixed ON rate. ON index is SONIA. */
  public void rateSonia2CutOffValuation2Sensitivity() {
    // publication=0, cutoff=2, effective offset=0, TS: Fixing 2
    LocalDate[] valuationDate = {date(2015, 1, 9), date(2015, 1, 12)};
    OvernightAveragedRateObservation ro =
        OvernightAveragedRateObservation.of(GBP_SONIA, FIXING_START_DATE, FIXING_END_DATE, 2);
    OvernightIndexRates mockRates = mock(OvernightIndexRates.class);
    when(mockRates.getIndex()).thenReturn(GBP_SONIA);
    SimpleRatesProvider simpleProv = new SimpleRatesProvider(mockRates);

    LocalDateDoubleTimeSeriesBuilder tsb = LocalDateDoubleTimeSeries.builder();
    int lastFixing = 3;
    for (int i = 0; i < lastFixing; i++) {
      tsb.put(FIXING_DATES[i], FIXING_RATES[i]);
    }
    int nRates = FIXING_DATES.length;
    OvernightIndexRates[] mockRatesUp = new OvernightIndexRates[nRates];
    SimpleRatesProvider[] simpleProvUp = new SimpleRatesProvider[nRates];
    OvernightIndexRates[] mockRatesDw = new OvernightIndexRates[nRates];
    SimpleRatesProvider[] simpleProvDw = new SimpleRatesProvider[nRates];
    OvernightIndexRates mockRatesPeriodUp = mock(OvernightIndexRates.class);
    SimpleRatesProvider simpleProvPeriodUp = new SimpleRatesProvider(mockRatesPeriodUp);
    OvernightIndexRates mockRatesPeriodDw = mock(OvernightIndexRates.class);
    SimpleRatesProvider simpleProvPeriodDw = new SimpleRatesProvider(mockRatesPeriodDw);

    when(mockRates.getTimeSeries()).thenReturn(tsb.build());
    for (int i = 0; i < lastFixing; i++) {
      when(mockRates.rate(FIXING_DATES[i])).thenReturn(FIXING_RATES[i]);
      when(mockRates.ratePointSensitivity(FIXING_DATES[i])).thenReturn(
          PointSensitivityBuilder.none());
    }
    for (int i = lastFixing; i < FIXING_DATES.length; i++) {
      when(mockRates.rate(FIXING_DATES[i])).thenReturn(FORWARD_RATES[i]);
      LocalDate fixingStartDate = GBP_SONIA.calculateEffectiveFromFixing(FIXING_DATES[i]);
      LocalDate fixingEndDate = GBP_SONIA.calculateMaturityFromEffective(fixingStartDate);
      PointSensitivityBuilder pointSensitivity = OvernightRateSensitivity.of(GBP_SONIA, FIXING_DATES[i],
          fixingEndDate, GBP_SONIA.getCurrency(), 1d);
      when(mockRates.ratePointSensitivity(FIXING_DATES[i])).thenReturn(pointSensitivity);
    }
    double investmentFactor = 1.0;
    double afApprox = 0.0;
    for (int i = lastFixing; i < 5; i++) {
      LocalDate endDate = GBP_SONIA.calculateMaturityFromEffective(FIXING_DATES[i]);
      double af = GBP_SONIA.getDayCount().yearFraction(FIXING_DATES[i], endDate);
      afApprox += af;
      investmentFactor *= 1.0d + af * FORWARD_RATES[i];
    }
    double rateCmp = (investmentFactor - 1.0d) / afApprox;
    when(mockRates.periodRate(FIXING_DATES[lastFixing], FIXING_DATES[5])).thenReturn(rateCmp);
    PointSensitivityBuilder periodSensitivity = OvernightRateSensitivity.of(GBP_SONIA, FIXING_DATES[lastFixing],
        FIXING_DATES[5], GBP_SONIA.getCurrency(), 1d);
    when(mockRates.periodRatePointSensitivity(FIXING_DATES[lastFixing], FIXING_DATES[5]))
        .thenReturn(periodSensitivity);
    setRatesProviders(
        mockRatesUp, simpleProvUp, mockRatesDw, simpleProvDw,
        mockRatesPeriodUp, simpleProvPeriodUp, mockRatesPeriodDw, simpleProvPeriodDw,
        ro, GBP_SONIA, FIXING_DATES[lastFixing], FIXING_DATES[5], rateCmp, tsb);
    when(mockRatesPeriodUp.getTimeSeries()).thenReturn(tsb.build());
    when(mockRatesPeriodDw.getTimeSeries()).thenReturn(tsb.build());
    for (int i = 0; i < lastFixing; i++) {
      when(mockRatesPeriodUp.rate(FIXING_DATES[i])).thenReturn(FIXING_RATES[i]);
      when(mockRatesPeriodDw.rate(FIXING_DATES[i])).thenReturn(FIXING_RATES[i]);
      for (int j = 0; j < nRates; ++j) {
        when(mockRatesUp[j].rate(FIXING_DATES[i])).thenReturn(FIXING_RATES[i]);
        when(mockRatesDw[j].rate(FIXING_DATES[i])).thenReturn(FIXING_RATES[i]);
      }
    }
    for (int loopvaldate = 0; loopvaldate < 2; loopvaldate++) {
      when(mockRates.getValuationDate()).thenReturn(valuationDate[loopvaldate]);
      when(mockRatesPeriodUp.getValuationDate()).thenReturn(valuationDate[loopvaldate]);
      when(mockRatesPeriodDw.getValuationDate()).thenReturn(valuationDate[loopvaldate]);
      PointSensitivityBuilder sensitivityBuilderComputed = OBS_FN_APPROX_FWD.rateSensitivity(ro,
          DUMMY_ACCRUAL_START_DATE, DUMMY_ACCRUAL_END_DATE, simpleProv);

      PointSensitivityBuilder sensitivityBuilderExpected1 = PointSensitivityBuilder.none();
      for (int i = 0; i < nRates; ++i) {
        when(mockRatesUp[i].getValuationDate()).thenReturn(valuationDate[loopvaldate]);
        when(mockRatesDw[i].getValuationDate()).thenReturn(valuationDate[loopvaldate]);
        double rateUp = OBS_FN_APPROX_FWD.rate(ro, DUMMY_ACCRUAL_START_DATE, DUMMY_ACCRUAL_END_DATE, simpleProvUp[i]);
        double rateDw = OBS_FN_APPROX_FWD.rate(ro, DUMMY_ACCRUAL_START_DATE, DUMMY_ACCRUAL_END_DATE, simpleProvDw[i]);
        double res = 0.5 * (rateUp - rateDw) / EPS_FD;
        LocalDate fixingStartDate = GBP_SONIA.calculateEffectiveFromFixing(FIXING_DATES[i]);
        LocalDate fixingEndDate = GBP_SONIA.calculateMaturityFromEffective(fixingStartDate);
        sensitivityBuilderExpected1 = res == 0.0 ? sensitivityBuilderExpected1 : sensitivityBuilderExpected1
            .combinedWith(OvernightRateSensitivity.of(GBP_SONIA, FIXING_DATES[i], fixingEndDate,
                GBP_SONIA.getCurrency(), res));
      }
      double ratePeriodUp = OBS_FN_APPROX_FWD.rate(ro, DUMMY_ACCRUAL_START_DATE, DUMMY_ACCRUAL_END_DATE,
          simpleProvPeriodUp);
      double ratePeriodDw = OBS_FN_APPROX_FWD.rate(ro, DUMMY_ACCRUAL_START_DATE, DUMMY_ACCRUAL_END_DATE,
          simpleProvPeriodDw);
      double periodSensitivityExpected = 0.5 * (ratePeriodUp - ratePeriodDw) / EPS_FD;
      PointSensitivityBuilder sensitivityBuilderExpected2 = OvernightRateSensitivity.of(GBP_SONIA,
          FIXING_DATES[lastFixing], FIXING_DATES[5], GBP_SONIA.getCurrency(), periodSensitivityExpected);
      PointSensitivityBuilder sensitivityBuilderExpected = sensitivityBuilderExpected1
          .combinedWith(sensitivityBuilderExpected2);

      assertTrue(sensitivityBuilderComputed.build().normalized().equalWithTolerance(
          sensitivityBuilderExpected.build().normalized(), EPS_FD));
    }
  }

  /** No cutoff period and two already fixed ON rate. ON index is SONIA. */
  public void rateSonia0CutOffValuation2() {
    // publication=0, cutoff=0, effective offset=0, TS: Fixing 2
    LocalDate[] valuationDate = {date(2015, 1, 9), date(2015, 1, 12)};
    OvernightAveragedRateObservation ro =
        OvernightAveragedRateObservation.of(GBP_SONIA, FIXING_START_DATE, FIXING_END_DATE, 0);
    OvernightIndexRates mockRates = mock(OvernightIndexRates.class);
    when(mockRates.getIndex()).thenReturn(GBP_SONIA);
    SimpleRatesProvider simpleProv = new SimpleRatesProvider(mockRates);

    LocalDateDoubleTimeSeriesBuilder tsb = LocalDateDoubleTimeSeries.builder();
    int lastFixing = 3;
    for (int i = 0; i < lastFixing; i++) {
      tsb.put(FIXING_DATES[i], FIXING_RATES[i]);
    }
    when(mockRates.getTimeSeries()).thenReturn(tsb.build());
    for (int i = 0; i < lastFixing; i++) {
      when(mockRates.rate(FIXING_DATES[i])).thenReturn(FIXING_RATES[i]);
    }
    for (int i = lastFixing; i < FIXING_DATES.length; i++) {
      when(mockRates.rate(FIXING_DATES[i])).thenReturn(FORWARD_RATES[i]);
    }
    double afKnown = 0.0;
    double accruedKnown = 0.0;
    for (int i = 0; i < lastFixing - 1; i++) {
      LocalDate fixingknown = FIXING_DATES[i + 1];
      LocalDate endDateKnown = GBP_SONIA.calculateMaturityFromEffective(fixingknown);
      double af = GBP_SONIA.getDayCount().yearFraction(fixingknown, endDateKnown);
      afKnown += af;
      accruedKnown += FIXING_RATES[i + 1] * af;
    }
    double investmentFactor = 1.0;
    double afApprox = 0.0;
    for (int i = lastFixing; i < 6; i++) {
      LocalDate endDate = GBP_SONIA.calculateMaturityFromEffective(FIXING_DATES[i]);
      double af = GBP_SONIA.getDayCount().yearFraction(FIXING_DATES[i], endDate);
      afApprox += af;
      investmentFactor *= 1.0d + af * FORWARD_RATES[i];
    }
    double rateCmp = (investmentFactor - 1.0d) / afApprox;
    when(mockRates.periodRate(FIXING_DATES[lastFixing], FIXING_DATES[6])).thenReturn(rateCmp);
    double rateExpected = (accruedKnown + Math.log(1.0 + rateCmp * afApprox))
        / (afKnown + afApprox);
    for (int loopvaldate = 0; loopvaldate < 2; loopvaldate++) {
      when(mockRates.getValuationDate()).thenReturn(valuationDate[loopvaldate]);
      double rateComputed = OBS_FN_APPROX_FWD.rate(ro, DUMMY_ACCRUAL_START_DATE, DUMMY_ACCRUAL_END_DATE, simpleProv);
      assertEquals(rateComputed, rateExpected, TOLERANCE_RATE);
    }
  }

  /** Test rate sensitivity against FD approximation. 
   * No cutoff period and two already fixed ON rate. ON index is SONIA. */
  public void rateSonia0CutOffValuation2Sensitivity() {
    // publication=0, cutoff=0, effective offset=0, TS: Fixing 2
    LocalDate[] valuationDate = {date(2015, 1, 9), date(2015, 1, 12)};
    OvernightAveragedRateObservation ro =
        OvernightAveragedRateObservation.of(GBP_SONIA, FIXING_START_DATE, FIXING_END_DATE, 0);
    OvernightIndexRates mockRates = mock(OvernightIndexRates.class);
    when(mockRates.getIndex()).thenReturn(GBP_SONIA);
    SimpleRatesProvider simpleProv = new SimpleRatesProvider(mockRates);

    LocalDateDoubleTimeSeriesBuilder tsb = LocalDateDoubleTimeSeries.builder();
    int lastFixing = 3;
    for (int i = 0; i < lastFixing; i++) {
      tsb.put(FIXING_DATES[i], FIXING_RATES[i]);
    }
    int nRates = FIXING_DATES.length;
    OvernightIndexRates[] mockRatesUp = new OvernightIndexRates[nRates];
    SimpleRatesProvider[] simpleProvUp = new SimpleRatesProvider[nRates];
    OvernightIndexRates[] mockRatesDw = new OvernightIndexRates[nRates];
    SimpleRatesProvider[] simpleProvDw = new SimpleRatesProvider[nRates];
    OvernightIndexRates mockRatesPeriodUp = mock(OvernightIndexRates.class);
    SimpleRatesProvider simpleProvPeriodUp = new SimpleRatesProvider(mockRatesPeriodUp);
    OvernightIndexRates mockRatesPeriodDw = mock(OvernightIndexRates.class);
    SimpleRatesProvider simpleProvPeriodDw = new SimpleRatesProvider(mockRatesPeriodDw);

    when(mockRates.getTimeSeries()).thenReturn(tsb.build());
    for (int i = 0; i < lastFixing; i++) {
      when(mockRates.rate(FIXING_DATES[i])).thenReturn(FIXING_RATES[i]);
      when(mockRates.ratePointSensitivity(FIXING_DATES[i])).thenReturn(
          PointSensitivityBuilder.none());
    }
    for (int i = lastFixing; i < FIXING_DATES.length; i++) {
      when(mockRates.rate(FIXING_DATES[i])).thenReturn(FORWARD_RATES[i]);
      LocalDate fixingStartDate = GBP_SONIA.calculateEffectiveFromFixing(FIXING_DATES[i]);
      LocalDate fixingEndDate = GBP_SONIA.calculateMaturityFromEffective(fixingStartDate);
      PointSensitivityBuilder pointSensitivity = OvernightRateSensitivity.of(GBP_SONIA, FIXING_DATES[i],
          fixingEndDate, GBP_SONIA.getCurrency(), 1d);
      when(mockRates.ratePointSensitivity(FIXING_DATES[i])).thenReturn(pointSensitivity);
    }
    double investmentFactor = 1.0;
    double afApprox = 0.0;
    for (int i = lastFixing; i < 6; i++) {
      LocalDate endDate = GBP_SONIA.calculateMaturityFromEffective(FIXING_DATES[i]);
      double af = GBP_SONIA.getDayCount().yearFraction(FIXING_DATES[i], endDate);
      afApprox += af;
      investmentFactor *= 1.0d + af * FORWARD_RATES[i];
    }
    double rateCmp = (investmentFactor - 1.0d) / afApprox;
    when(mockRates.periodRate(FIXING_DATES[lastFixing],
        FIXING_DATES[6])).thenReturn(rateCmp);
    PointSensitivityBuilder periodSensitivity = OvernightRateSensitivity.of(GBP_SONIA, FIXING_DATES[lastFixing],
        FIXING_DATES[6], GBP_SONIA.getCurrency(), 1d);
    when(mockRates.periodRatePointSensitivity(FIXING_DATES[lastFixing], FIXING_DATES[6]))
        .thenReturn(periodSensitivity);
    setRatesProviders(
        mockRatesUp, simpleProvUp, mockRatesDw, simpleProvDw,
        mockRatesPeriodUp, simpleProvPeriodUp, mockRatesPeriodDw, simpleProvPeriodDw,
        ro, GBP_SONIA, FIXING_DATES[lastFixing], FIXING_DATES[6], rateCmp, tsb);
    when(mockRatesPeriodUp.getTimeSeries()).thenReturn(tsb.build());
    when(mockRatesPeriodDw.getTimeSeries()).thenReturn(tsb.build());
    for (int i = 0; i < lastFixing; i++) {
      when(mockRatesPeriodUp.rate(FIXING_DATES[i])).thenReturn(FIXING_RATES[i]);
      when(mockRatesPeriodDw.rate(FIXING_DATES[i])).thenReturn(FIXING_RATES[i]);
      for (int j = 0; j < nRates; ++j) {
        when(mockRatesUp[j].rate(FIXING_DATES[i])).thenReturn(FIXING_RATES[i]);
        when(mockRatesDw[j].rate(FIXING_DATES[i])).thenReturn(FIXING_RATES[i]);
      }
    }
    for (int loopvaldate = 0; loopvaldate < 2; loopvaldate++) {
      when(mockRates.getValuationDate()).thenReturn(valuationDate[loopvaldate]);
      when(mockRatesPeriodUp.getValuationDate()).thenReturn(valuationDate[loopvaldate]);
      when(mockRatesPeriodDw.getValuationDate()).thenReturn(valuationDate[loopvaldate]);
      PointSensitivityBuilder sensitivityBuilderComputed = OBS_FN_APPROX_FWD.rateSensitivity(ro,
          DUMMY_ACCRUAL_START_DATE, DUMMY_ACCRUAL_END_DATE, simpleProv);

      PointSensitivityBuilder sensitivityBuilderExpected1 = PointSensitivityBuilder.none();
      for (int i = 0; i < nRates; ++i) {
        when(mockRatesUp[i].getValuationDate()).thenReturn(valuationDate[loopvaldate]);
        when(mockRatesDw[i].getValuationDate()).thenReturn(valuationDate[loopvaldate]);
        double rateUp = OBS_FN_APPROX_FWD.rate(ro, DUMMY_ACCRUAL_START_DATE, DUMMY_ACCRUAL_END_DATE, simpleProvUp[i]);
        double rateDw = OBS_FN_APPROX_FWD.rate(ro, DUMMY_ACCRUAL_START_DATE, DUMMY_ACCRUAL_END_DATE, simpleProvDw[i]);
        double res = 0.5 * (rateUp - rateDw) / EPS_FD;
        LocalDate fixingStartDate = GBP_SONIA.calculateEffectiveFromFixing(FIXING_DATES[i]);
        LocalDate fixingEndDate = GBP_SONIA.calculateMaturityFromEffective(fixingStartDate);
        sensitivityBuilderExpected1 = res == 0.0 ? sensitivityBuilderExpected1 : sensitivityBuilderExpected1
            .combinedWith(OvernightRateSensitivity.of(GBP_SONIA, FIXING_DATES[i], fixingEndDate,
                GBP_SONIA.getCurrency(), res));
      }
      double ratePeriodUp = OBS_FN_APPROX_FWD.rate(ro, DUMMY_ACCRUAL_START_DATE, DUMMY_ACCRUAL_END_DATE,
          simpleProvPeriodUp);
      double ratePeriodDw = OBS_FN_APPROX_FWD.rate(ro, DUMMY_ACCRUAL_START_DATE, DUMMY_ACCRUAL_END_DATE,
          simpleProvPeriodDw);
      double periodSensitivityExpected = 0.5 * (ratePeriodUp - ratePeriodDw) / EPS_FD;
      PointSensitivityBuilder sensitivityBuilderExpected2 = OvernightRateSensitivity.of(GBP_SONIA,
          FIXING_DATES[lastFixing], FIXING_DATES[6], GBP_SONIA.getCurrency(), periodSensitivityExpected);
      PointSensitivityBuilder sensitivityBuilderExpected = sensitivityBuilderExpected1
          .combinedWith(sensitivityBuilderExpected2);

      assertTrue(sensitivityBuilderComputed.build().normalized().equalWithTolerance(
          sensitivityBuilderExpected.build().normalized(), EPS_FD));
    }
  }

  /** One past fixing missing. Checking the error thrown. */
  public void rateFedFund2CutOffValuation2MissingFixing() {
    // publication=1, cutoff=2, effective offset=0, TS: Fixing 2
    LocalDate valuationDate = date(2015, 1, 13);
    OvernightAveragedRateObservation ro =
        OvernightAveragedRateObservation.of(USD_FED_FUND, FIXING_START_DATE, FIXING_END_DATE, 2);
    OvernightIndexRates mockRates = mock(OvernightIndexRates.class);
    when(mockRates.getIndex()).thenReturn(USD_FED_FUND);
    when(mockRates.getValuationDate()).thenReturn(valuationDate);
    SimpleRatesProvider simpleProv = new SimpleRatesProvider(valuationDate, mockRates);

    LocalDateDoubleTimeSeriesBuilder tsb = LocalDateDoubleTimeSeries.builder();
    int lastFixing = 2;
    for (int i = 0; i < lastFixing; i++) {
      tsb.put(FIXING_DATES[i], FIXING_RATES[i]);
    }
    when(mockRates.getTimeSeries()).thenReturn(tsb.build());
    for (int i = 0; i < lastFixing; i++) {
      when(mockRates.rate(FIXING_DATES[i])).thenReturn(FIXING_RATES[i]);
    }
    for (int i = lastFixing; i < FIXING_DATES.length; i++) {
      when(mockRates.rate(FIXING_DATES[i])).thenReturn(FORWARD_RATES[i]);
    }
    assertThrows(
        () -> OBS_FN_APPROX_FWD.rate(ro, DUMMY_ACCRUAL_START_DATE, DUMMY_ACCRUAL_END_DATE, simpleProv),
        PricingException.class);
    assertThrows(
        () -> OBS_FN_APPROX_FWD.rateSensitivity(ro, DUMMY_ACCRUAL_START_DATE, DUMMY_ACCRUAL_END_DATE, simpleProv),
        PricingException.class);
  }

  /** Two days cutoff, all ON rates already fixed. */
  public void rateFedFund2CutOffValuationEnd() {
    // publication=1, cutoff=2, effective offset=0, TS: Fixing all
    LocalDate[] valuationDate = {date(2015, 1, 15), date(2015, 1, 16)};
    OvernightAveragedRateObservation ro =
        OvernightAveragedRateObservation.of(USD_FED_FUND, FIXING_START_DATE, FIXING_END_DATE, 2);
    OvernightIndexRates mockRates = mock(OvernightIndexRates.class);
    when(mockRates.getIndex()).thenReturn(USD_FED_FUND);
    SimpleRatesProvider simpleProv = new SimpleRatesProvider(mockRates);

    LocalDateDoubleTimeSeriesBuilder tsb = LocalDateDoubleTimeSeries.builder();
    int lastFixing = 6;
    for (int i = 0; i < lastFixing; i++) {
      tsb.put(FIXING_DATES[i], FIXING_RATES[i]);
    }
    when(mockRates.getTimeSeries()).thenReturn(tsb.build());
    for (int i = 0; i < lastFixing; i++) {
      when(mockRates.rate(FIXING_DATES[i])).thenReturn(FIXING_RATES[i]);
    }
    for (int i = lastFixing; i < FIXING_DATES.length; i++) {
      when(mockRates.rate(FIXING_DATES[i])).thenReturn(FORWARD_RATES[i]);
    }
    double afKnown = 0.0;
    double accruedKnown = 0.0;
    for (int i = 0; i < 4; i++) {
      LocalDate fixingknown = FIXING_DATES[i + 1];
      LocalDate endDateKnown = USD_FED_FUND.calculateMaturityFromEffective(fixingknown);
      double af = USD_FED_FUND.getDayCount().yearFraction(fixingknown, endDateKnown);
      afKnown += af;
      accruedKnown += FIXING_RATES[i + 1] * af;
    }
    LocalDate fixingCutOff = FIXING_DATES[5];
    LocalDate endDateCutOff = USD_FED_FUND.calculateMaturityFromEffective(fixingCutOff);
    double afCutOff = USD_FED_FUND.getDayCount().yearFraction(fixingCutOff, endDateCutOff);
    double rateExpected = (accruedKnown + FIXING_RATES[4] * afCutOff)
        / (afKnown + afCutOff);
    for (int loopvaldate = 0; loopvaldate < 2; loopvaldate++) {
      when(mockRates.getValuationDate()).thenReturn(valuationDate[loopvaldate]);
      double rateComputed = OBS_FN_APPROX_FWD.rate(ro, DUMMY_ACCRUAL_START_DATE, DUMMY_ACCRUAL_END_DATE, simpleProv);
      assertEquals(rateComputed, rateExpected, TOLERANCE_RATE);
    }
  }

  /** Test rate Sensitivity. Two days cutoff, all ON rates already fixed. Thus none is expected*/
  public void rateFedFund2CutOffValuationEndSensitivity() {
    // publication=1, cutoff=2, effective offset=0, TS: Fixing all
    LocalDate[] valuationDate = {date(2015, 1, 15), date(2015, 1, 16)};
    OvernightAveragedRateObservation ro =
        OvernightAveragedRateObservation.of(USD_FED_FUND, FIXING_START_DATE, FIXING_END_DATE, 2);
    OvernightIndexRates mockRates = mock(OvernightIndexRates.class);
    when(mockRates.getIndex()).thenReturn(USD_FED_FUND);
    SimpleRatesProvider simpleProv = new SimpleRatesProvider(mockRates);

    LocalDateDoubleTimeSeriesBuilder tsb = LocalDateDoubleTimeSeries.builder();
    int lastFixing = 6;
    for (int i = 0; i < lastFixing; i++) {
      tsb.put(FIXING_DATES[i], FIXING_RATES[i]);
    }
    when(mockRates.getTimeSeries()).thenReturn(tsb.build());
    for (int loopvaldate = 0; loopvaldate < 2; loopvaldate++) {
      when(mockRates.getValuationDate()).thenReturn(valuationDate[loopvaldate]);
      PointSensitivityBuilder sensitivityBuilderExpected = OBS_FN_APPROX_FWD.rateSensitivity(ro,
          DUMMY_ACCRUAL_START_DATE, DUMMY_ACCRUAL_END_DATE, simpleProv);
      assertEquals(sensitivityBuilderExpected, PointSensitivityBuilder.none());
    }
  }

  //-------------------------------------------------------------------------
  private static final CurveInterpolator INTERPOLATOR = CurveInterpolators.DOUBLE_QUADRATIC;
  private static LocalDateDoubleTimeSeriesBuilder TIME_SERIES_BUILDER = LocalDateDoubleTimeSeries.builder();
  static {
    for (int i = 0; i < FIXING_DATES.length; i++) {
      TIME_SERIES_BUILDER.put(FIXING_DATES[i], FIXING_RATES[i]);
    }
  }
  private static final RatesFiniteDifferenceSensitivityCalculator CAL_FD =
      new RatesFiniteDifferenceSensitivityCalculator(EPS_FD);

  /** Test curve parameter sensitivity with finite difference sensitivity calculator. No cutoff period*/
  public void rateFedFundNoCutOffForwardParameterSensitivity() {
    LocalDate[] valuationDate = {date(2015, 1, 1), date(2015, 1, 8)};
    DoubleArray time_usd = DoubleArray.of(0.0, 0.5, 1.0, 2.0, 5.0, 10.0);
    DoubleArray rate_usd = DoubleArray.of(0.0100, 0.0110, 0.0115, 0.0130, 0.0135, 0.0135);
    OvernightAveragedRateObservation ro =
        OvernightAveragedRateObservation.of(USD_FED_FUND, FIXING_START_DATE, FIXING_END_DATE, 0);

    for (int loopvaldate = 0; loopvaldate < 2; loopvaldate++) {
      Curve fedFundCurve = InterpolatedNodalCurve.of(
          Curves.zeroRates("USD-Fed-Fund", ACT_ACT_ISDA), time_usd, rate_usd, INTERPOLATOR);
      ImmutableRatesProvider prov = ImmutableRatesProvider.builder()
          .valuationDate(valuationDate[loopvaldate])
          .indexCurves(ImmutableMap.of(USD_FED_FUND, fedFundCurve))
          .timeSeries(ImmutableMap.of(USD_FED_FUND, TIME_SERIES_BUILDER.build()))
          .build();
      PointSensitivityBuilder sensitivityBuilderComputed =
          OBS_FN_APPROX_FWD.rateSensitivity(ro, DUMMY_ACCRUAL_START_DATE, DUMMY_ACCRUAL_END_DATE, prov);
      CurveCurrencyParameterSensitivities parameterSensitivityComputed =
          prov.curveParameterSensitivity(sensitivityBuilderComputed.build());
      CurveCurrencyParameterSensitivities parameterSensitivityExpected =
          CAL_FD.sensitivity(prov, (p) -> CurrencyAmount.of(USD_FED_FUND.getCurrency(),
              OBS_FN_APPROX_FWD.rate(ro, DUMMY_ACCRUAL_START_DATE, DUMMY_ACCRUAL_END_DATE, (p))));
      assertTrue(parameterSensitivityComputed.equalWithTolerance(parameterSensitivityExpected, EPS_FD * 10.0));
    }
  }

  /** Test curve parameter sensitivity with finite difference sensitivity calculator. Two days cutoff period*/
  public void rateFedFund2CutOffForwardParameterSensitivity() {
    LocalDate[] valuationDate = {date(2015, 1, 1), date(2015, 1, 8)};
    DoubleArray time_usd = DoubleArray.of(0.0, 0.5, 1.0, 2.0, 5.0, 10.0);
    DoubleArray rate_usd = DoubleArray.of(0.0100, 0.0110, 0.0115, 0.0130, 0.0135, 0.0135);
    OvernightAveragedRateObservation ro =
        OvernightAveragedRateObservation.of(USD_FED_FUND, FIXING_START_DATE, FIXING_END_DATE, 2);

    for (int loopvaldate = 0; loopvaldate < 2; loopvaldate++) {
      Curve fedFundCurve = InterpolatedNodalCurve.of(
          Curves.zeroRates("USD-Fed-Fund", ACT_ACT_ISDA), time_usd, rate_usd, INTERPOLATOR);
      ImmutableRatesProvider prov = ImmutableRatesProvider.builder()
          .valuationDate(valuationDate[loopvaldate])
          .indexCurves(ImmutableMap.of(USD_FED_FUND, fedFundCurve))
          .timeSeries(ImmutableMap.of(USD_FED_FUND, TIME_SERIES_BUILDER.build()))
          .build();
      PointSensitivityBuilder sensitivityBuilderComputed =
          OBS_FN_APPROX_FWD.rateSensitivity(ro, DUMMY_ACCRUAL_START_DATE, DUMMY_ACCRUAL_END_DATE, prov);
      CurveCurrencyParameterSensitivities parameterSensitivityComputed =
          prov.curveParameterSensitivity(sensitivityBuilderComputed.build());
      CurveCurrencyParameterSensitivities parameterSensitivityExpected =
          CAL_FD.sensitivity(prov, (p) -> CurrencyAmount.of(USD_FED_FUND.getCurrency(),
              OBS_FN_APPROX_FWD.rate(ro, DUMMY_ACCRUAL_START_DATE, DUMMY_ACCRUAL_END_DATE, (p))));
      assertTrue(parameterSensitivityComputed.equalWithTolerance(parameterSensitivityExpected, EPS_FD * 10.0));
    }
  }

}
