import {Component, Input} from '@angular/core';
import {ActivatedRoute, Params} from "@angular/router";
import {Observable} from 'rxjs';
import {map, tap} from "rxjs/operators";

import {AnalysisDataService} from "../analysisData.service";
import {AnalysisResults} from "../datastructures/diffs";
import {
  compareVersions,
  dictionaryToKeyValueObjectList,
  KeyValueObject,
  selectedRegressions,
  verToNum
} from "../utils";

@Component({
  selector : 'file',
  templateUrl : './analysisResultsOverview.component.html',
  styleUrls : [ './analysisResultsOverview.component.css' ]
})

export class AnalysisResultsOverviewComponent {
  analysisResults$: Observable<KeyValueObject<AnalysisResults>[]>;
  resolvedInitVersionInfo: AnalysisResults|undefined;
  hidePrototypes: boolean = true;
  hideClientCoverage: boolean = true;

  NoRegretsPlusBenchmark: boolean;

  // false indicates NoRegrets

  constructor(private route: ActivatedRoute,
              private analysisDataService: AnalysisDataService) {
    this.route.params
        .pipe(
            tap((p: Params) => {this.NoRegretsPlusBenchmark = p.key.includes('NoRegretsPlus')}))
        .subscribe(
            (p: Params) => this.analysisResults$ =
                analysisDataService.getAnalysisResults(p.key).pipe(
                    map(res => dictionaryToKeyValueObjectList(res,
                                                              compareVersions)),
                    tap(res => this.resolvedInitVersionInfo = res[0].value)));
  }

  containsTypeRegressions(benchmarkInfo: KeyValueObject<AnalysisResults>) {
    return selectedRegressions(benchmarkInfo.value.regressionInfo.regressions,
                               this.getToolName())
               .length != 0;
  }

  getToolName(): string { return this.NoRegretsPlusBenchmark ? 'NoRegretsPlus' : 'NoRegrets'; }
}
