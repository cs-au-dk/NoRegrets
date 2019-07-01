import {HttpClient} from "@angular/common/http";
import {Injectable} from "@angular/core";
import {Dictionary} from 'lodash';
import {Observable} from 'rxjs';
import {map} from "rxjs/operators";

import {AnalysisResults} from "./datastructures/diffs";
import {BenchmarkStatus} from "./datastructures/status";

@Injectable()
export class AnalysisDataService {
  private baseUrl = '/caching/';

  constructor(private http: HttpClient) {}

  getStatus(): Observable<Dictionary<BenchmarkStatus>> {
    return this.http.get(`${this.baseUrl}benchmark-status.json`)
               .pipe(map(x => (x as any).diffKeys)) as
           Observable<Dictionary<BenchmarkStatus>>;
  }

  getAnalysisResults(key): Observable<Dictionary<AnalysisResults>> {
    return this.http.get(this.baseUrl + key + ".json").pipe(map(response => {
      let json = response;
      for (let k of Object.getOwnPropertyNames(json)) {
        json[k] = AnalysisResults.fromJson(json[k]);
      }
      return json as Dictionary<AnalysisResults>;
    }));
  }
}
