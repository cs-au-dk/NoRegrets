import { Component, OnInit } from '@angular/core';
import {BenchmarkStatusService} from "../benchmark-status.service";
import {Observable} from "rxjs/Observable";
import {BenchmarksStatus, IDictionary, Status} from "../datastructures/status";
import {Router} from "@angular/router";
import {_getEntries, verToNum} from "../utils";
import * as _ from "lodash";
import "rxjs/add/operator/map"

@Component({
  selector: 'app-status',
  templateUrl: './client-details.component.html',
  styleUrls: ['./client-details.component.css']
})
export class ClientDetailsComponent implements OnInit {

  status: Observable<BenchmarksStatus>;
  groupedStatus: Observable<IDictionary<IDictionary<Status>>>;

  constructor(private statusService: BenchmarkStatusService,
              private router: Router) {
    this.status = this.statusService.getStatus();
    this.groupedStatus = this.status.map((status) => {
      let obs = status.observations;

      let pairs = _.toPairs(obs);
      let groupedByLibName = _.mapValues(_.groupBy(pairs, (elem) => elem[0].split("@")[0]), (v) => _.fromPairs(v));

      let r = _.mapValues(groupedByLibName, (libVersionToClient) => {
        let r = {};
        for(let lib in libVersionToClient) {
          for(let client in libVersionToClient[lib]) {
            if(typeof r[client] === 'undefined') {
              r[client] = {};
            }
            r[client][lib] = libVersionToClient[lib][client];
          }
        }
        return r;
      });
      return r as any;
    });
  }

  ngOnInit() {
  }


  getSingleClientDiffKey(clientWithVersion, libraryName) {
    let splitted = clientWithVersion.split("@");
    let clientName = splitted[0];
    let clientVersion = splitted[1];
    return 'diffClient-' + clientName + "-" + clientVersion + "-" + libraryName;
  }

  getEntries(obj, sorted) {
    let entries = _getEntries(obj);
    if(sorted)
      return entries.sort((a, b) => verToNum(a.key) - verToNum(b.key));
    else
      return entries;
  }
}
