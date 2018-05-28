import {Http} from "@angular/http";
import {Injectable} from "@angular/core";
import {Observable} from 'rxjs/Observable';
import {KnowledgeDiff} from "../datastructures/diffs";
import {IDictionary} from "../datastructures/status";

@Injectable()
export class DiffService {
  private headers = new Headers({'Content-Type': 'application/json'});
  private baseUrl = 'caching/';

  constructor(private http: Http) {
  }

  getDiff(key): Observable<IDictionary<KnowledgeDiff>> {
    return this.http.get(this.baseUrl + key + ".json").map(response => {
      let json = response.json();
      for(let k of Object.getOwnPropertyNames(json)) {
        json[k] = KnowledgeDiff.fromJson(json[k]);
      }
      return json as IDictionary<KnowledgeDiff>;
    });
  }
}
