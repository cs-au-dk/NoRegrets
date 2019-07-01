import {Injectable} from "@angular/core";
import {Observable} from 'rxjs';
import {TracingResult} from "../../../../api-inference/API-tracer/src/tracing-results";
import {HttpClient} from "@angular/common/http";
import {map} from "rxjs/operators";

@Injectable()
export class LogService {
  private headers = new Headers({'Content-Type': 'application/json'});
  private baseUrl = 'caching/';

  constructor(private http: HttpClient) {
  }

  getLearnedLog(key): Observable<string> {
    return this.http.get(this.baseUrl + key + ".json.log").pipe(map(response => {
      return response.toString();
    }));
  }
}
