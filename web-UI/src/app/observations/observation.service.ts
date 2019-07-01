import {Injectable} from "@angular/core";
import {Observable} from 'rxjs';
import {TracingResult} from "../../../../api-inference/API-tracer/src/tracing-results";
import {HttpClient} from "@angular/common/http";
import {map} from "rxjs/operators";

@Injectable()
export class ObservationService {
  private headers = new Headers({'Content-Type': 'application/json'});
  private baseUrl = 'caching/';

  constructor(private http: HttpClient) {
  }

  getObservations(key): Observable<TracingResult> {
    return this.http.get(this.baseUrl + key + ".json").pipe(map(response => {
      return TracingResult.fromJson(response);
    }));
  }
}
