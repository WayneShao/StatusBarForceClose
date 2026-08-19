package com.wayne.statusbarforceclose;

interface OptimizationRepository {
    OptimizationJournal loadOptimizationJournal();

    boolean commitOptimizationJournal(OptimizationJournal journal);
}
