import {Http} from "@angular/http";
import {Injectable} from "@angular/core";
import {Observable} from 'rxjs/Observable';
import {TracingResult} from "../../../../api-inference/API-tracer/src/tracing-results";

@Injectable()
export class LogService {
  private headers = new Headers({'Content-Type': 'application/json'});
  private baseUrl = 'caching/';

  constructor(private http: Http) {
  }

  getLearnedLog(key): Observable<string> {
    return this.http.get(this.baseUrl + key + ".json.log").map(response => {
      return response.text();
    });
  }
}
