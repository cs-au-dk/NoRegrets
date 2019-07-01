import {Component, OnInit} from '@angular/core';
import {Router} from "@angular/router";
import {groupBy, keys, sortBy} from "lodash"
import {Dictionary} from "lodash";
import {Observable} from "rxjs";
import {map, tap} from "rxjs/operators";

import {AnalysisDataService} from "../analysisData.service";
import {
  BenchmarkStatus,
} from "../datastructures/status";
import {dictionaryToKeyValueObjectList, KeyValueObject} from "../utils";

@Component({
  selector : 'app-diff-status',
  templateUrl : './status.component.html',
  styleUrls : [ './status.component.css' ]
})
export class StatusComponent implements OnInit {
  status: Observable<KeyValueObject<BenchmarkStatus[]>[]>;
  toggledRows: {[index: string]: boolean[]} = {};

  constructor(private statusService: AnalysisDataService,
              private router: Router) {
    this.status = this.statusService.getStatus().pipe(
        map(s => (groupBy<Dictionary<BenchmarkStatus>>(s, (s) => s.name))),
        map(s => sortBy(keys(s).map(k => {
              return {key : k, value : s[k]} as
                     KeyValueObject<BenchmarkStatus[]>
            }),
                        (pair) => pair.key)),
        tap(benchmarks =>
                benchmarks.forEach(b => this.toggledRows[b.key.toString()] =
                                       new Array(b.value.length).fill(false))));
  }

  ngOnInit() {}

  getBenchStatuses(statuses: Dictionary<BenchmarkStatus>) {
    return dictionaryToKeyValueObjectList(
        statuses,
        (a: BenchmarkStatus, b: BenchmarkStatus) => a.key.localeCompare(b.key));
  }

  toggleRow(key: PropertyKey, i: number) {
    const rows = this.toggledRows[key.toString()];
    rows[i] = !rows[i];

    let selectedCnt = rows[i] ? 1 : 2;
    for (let j = 0; j < rows.length; j++) {
      if (j === i) continue;

      if (rows[j] && selectedCnt <= 0) {
        rows[j] = false
      } else if (rows[j]) {
        selectedCnt--;
      }
    }
  }

  compare(key: PropertyKey) {
    const rows = this.toggledRows[key.toString()];
    this.status.subscribe(statuses => {
      const benchmarkGrp =
          statuses.find(s => s.key.toString() == key.toString());
      const selectedBenchmarks: BenchmarkStatus[] = [];
      for (let i = 0; i < rows.length; i++) {
        if (rows[i]) {
          selectedBenchmarks.push(benchmarkGrp.value[i]);
        }
      }
      if (selectedBenchmarks.length !== 2) {
        alert("Error: 2 benchmarks must be selected");
        return;
      }
      this.router.navigate(
          [ '/compare', selectedBenchmarks[0].key, selectedBenchmarks[1].key ]);
    });
  }
}
