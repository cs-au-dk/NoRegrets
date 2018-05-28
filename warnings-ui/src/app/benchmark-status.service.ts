import {Http} from "@angular/http";
import {Injectable} from "@angular/core";
import {Observable} from 'rxjs/Observable';
import {BenchmarksStatus} from "./datastructures/status";

@Injectable()
export class BenchmarkStatusService {
  private headers = new Headers({'Content-Type': 'application/json'});
  private statusUrl = '/caching/benchmark-status.json';

  constructor(private http: Http) { }

  getStatus(): Observable<BenchmarksStatus> {
    return this.http.get(this.statusUrl).map(response => BenchmarksStatus.fromJson(response.json()));
  }

}
