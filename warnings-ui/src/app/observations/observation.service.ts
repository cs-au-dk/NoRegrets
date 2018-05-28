import {Http} from "@angular/http";
import {Injectable} from "@angular/core";
import {Observable} from 'rxjs/Observable';
import {LearningFailure, TracingResult} from "../../../../api-inference/API-tracer/src/tracing-results";

@Injectable()
export class ObservationService {
  private headers = new Headers({'Content-Type': 'application/json'});
  private baseUrl = 'caching/';

  constructor(private http: Http) {
  }

  getObservations(key): Observable<TracingResult> {
    return this.http.get(this.baseUrl + key + ".json").map(response => {
      return TracingResult.fromJson(response.json());
    });
  }
}
