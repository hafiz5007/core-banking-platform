package com.bank.riskaml.application;

import com.bank.common.error.ResourceNotFoundException;
import com.bank.common.money.Money;
import com.bank.riskaml.adapter.out.persistence.AlertRepository;
import com.bank.riskaml.adapter.out.persistence.AmlCaseRepository;
import com.bank.riskaml.application.MonitoringEngine.Evaluation;
import com.bank.riskaml.domain.Alert;
import com.bank.riskaml.domain.AmlCase;
import com.bank.riskaml.domain.Decision;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Evaluates transactions against monitoring rules, raises alerts/cases on a breach, and manages
 * case closure with optional SAR filing (FR-AML-001/002/003).
 */
@Service
public class RiskService {

  private static final Logger log = LoggerFactory.getLogger(RiskService.class);

  private final MonitoringEngine monitoringEngine;
  private final AlertRepository alertRepository;
  private final AmlCaseRepository caseRepository;
  private final ChangeLogRecorder changeLog;

  public RiskService(
      MonitoringEngine monitoringEngine,
      AlertRepository alertRepository,
      AmlCaseRepository caseRepository,
      ChangeLogRecorder changeLog) {
    this.monitoringEngine = monitoringEngine;
    this.alertRepository = alertRepository;
    this.caseRepository = caseRepository;
    this.changeLog = changeLog;
  }

  @Transactional
  public EvaluationResult evaluate(
      String transactionRef, String accountRef, Money amount, String counterpartyCountry) {
    Evaluation evaluation = monitoringEngine.evaluate(amount, counterpartyCountry);
    UUID caseId = null;
    UUID alertId = null;
    if (evaluation.alerted()) {
      AmlCase amlCase = caseRepository.save(new AmlCase(accountRef, evaluation.ruleCode()));
      Alert alert =
          alertRepository.save(
              new Alert(
                  transactionRef, accountRef, amount, evaluation.ruleCode(), amlCase.getId()));
      caseId = amlCase.getId();
      alertId = alert.getId();
      changeLog.record(
          "AmlCase",
          amlCase.getId().toString(),
          com.bank.riskaml.domain.ChangeType.CREATE,
          null,
          "Opened case for rule " + evaluation.ruleCode() + " on " + transactionRef);
      log.info(
          "AML rule {} fired on {} -> decision={} case={}",
          evaluation.ruleCode(),
          transactionRef,
          evaluation.decision(),
          caseId);
    }
    return new EvaluationResult(evaluation.decision(), evaluation.ruleCode(), alertId, caseId);
  }

  @Transactional(readOnly = true)
  public AmlCase getCase(UUID id) {
    return caseRepository
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Case not found: " + id));
  }

  @Transactional
  public AmlCase closeCase(UUID id, String resolution, boolean fileSar) {
    AmlCase amlCase = getCase(id);
    amlCase.close(resolution, fileSar);
    if (fileSar) {
      log.info("SAR filed for case {}", id);
    }
    return caseRepository.save(amlCase);
  }

  public record EvaluationResult(Decision decision, String ruleCode, UUID alertId, UUID caseId) {}
}
