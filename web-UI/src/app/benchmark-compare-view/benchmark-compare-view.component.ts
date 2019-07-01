import {Component, OnInit} from '@angular/core';
import {ActivatedRoute, ParamMap, Router} from '@angular/router';
import {intersectionWith, join, sumBy, unzip} from 'lodash'
import {
  AsyncSubject,
  BehaviorSubject,
  forkJoin,
  Observable,
  of,
  zip
} from "rxjs";
import {flatMap, map, tap} from "rxjs/operators";

import {
  IDictionary
} from "../../../../api-inference/API-tracer/src/common-utils";
import {AnalysisDataService} from "../analysisData.service";
import {AnalysisResults, ClientDetail} from "../datastructures/diffs";
import {compareVersions, dictionaryToKeyValueObjectList} from "../utils";

@Component({
  selector : 'app-benchmark-compare-view',
  templateUrl : './benchmark-compare-view.component.html',
  styleUrls : [ './benchmark-compare-view.component.css' ]
})
export class BenchmarkCompareViewComponent implements OnInit {
  // Ideally, this would be an AsyncSubject, but I can't figure out how to call
  // complete on the subject when the observable it subscribes to completes.
  public zippedSharedClients$: BehaviorSubject<ClientDetail[][]> =
      new BehaviorSubject(new Array());
  public firstToolName$: BehaviorSubject<string> = new BehaviorSubject('');
  public secondToolName$: BehaviorSubject<string> = new BehaviorSubject('');

  constructor(private route: ActivatedRoute, private router: Router,
              private analysisDataService: AnalysisDataService) {}

  ngOnInit() {
    const routeObs = this.route.paramMap;

    const extractName = (key: string) =>
        key.includes('NoRegretsPlus') ? 'NoRegretsPlus' : 'NoRegrets';

    routeObs.pipe(map((params) => extractName(params.get('firstKey'))))
        .subscribe(this.firstToolName$);
    routeObs.pipe(map((params) => extractName(params.get('secondKey'))))
        .subscribe(this.secondToolName$);

    zip(routeObs.pipe(flatMap((params: ParamMap) =>
                                  this.analysisDataService.getAnalysisResults(
                                      params.get('firstKey')))),
        routeObs.pipe(flatMap((params: ParamMap) =>
                                  this.analysisDataService.getAnalysisResults(
                                      params.get('secondKey')))))
        .pipe(map(([ r1, r2 ]) => {
          const firstRes =
              dictionaryToKeyValueObjectList(r1, compareVersions)[1];
          const secondRes =
              dictionaryToKeyValueObjectList(r2, compareVersions)[1];

          const succeededCds = (cds: ClientDetail[]) =>
              cds.filter(cd => cd.succeeded);

          const cdsSucceeded1 = succeededCds(firstRes.value.clientDetails);
          const cdsSucceeded2 = succeededCds(secondRes.value.clientDetails);

          const sharedClients = intersectionWith(
              cdsSucceeded1, cdsSucceeded2,
              (cd1, cd2) => cd1.packageAtVersion === cd2.packageAtVersion);

          return sharedClients.map(cdShared => {
            const cdSharedPkg = cdShared.packageAtVersion;
            return [
              cdsSucceeded1.find(cd => cd.packageAtVersion === cdSharedPkg),
              cdsSucceeded2.find(cd => cd.packageAtVersion === cdSharedPkg)
            ];
          });
        }))
        .subscribe(this.zippedSharedClients$);
  }

  meanTime(benchmarkIdx: number): Observable<number> {
    return this.zippedSharedClients$.pipe(map((zippedSharedClients) => {
      // The subject zippedSharedClients is initialized to the empty list,
      // and reassigned to the actual client once the HTTP request
      // completes.
      if (zippedSharedClients.length === 0) return NaN;
      const unzipped = unzip(zippedSharedClients);
      const sum = sumBy(unzipped[benchmarkIdx], (cd) => cd.executionTimeMillis);
      return (sum / unzipped[benchmarkIdx].length)
    }));
  }

  totalSpeedRatio(): Observable<string> {
    return zip(this.meanTime(0), this.meanTime(1))
        .pipe(map(([ t1, t2 ]) => `${this.speedRatio(t1, t2)}`));
  }

  private speedRatio(t1: number, t2: number): string {
    const min = t1 > t2 ? t2 : t1;
    const max = t1 > t2 ? t1 : t2;
    return (max / min).toFixed(2);
  }

  speedRatioCD(cd1: ClientDetail, cd2: ClientDetail): string {
    return this.speedRatio(cd1.executionTimeMillis, cd2.executionTimeMillis);
  }
}
