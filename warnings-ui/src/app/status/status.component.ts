import { Component, OnInit } from '@angular/core';
import {BenchmarkStatusService} from "../benchmark-status.service";
import {Observable} from "rxjs/Observable";
import {BenchmarksStatus, IDictionary, Status} from "../datastructures/status";
import {Router} from "@angular/router";
import {_getEntries} from "../utils";
import {DiffService} from "../diffs/diffs";
import {KnowledgeDiff} from "../datastructures/diffs";

@Component({
  selector: 'app-diff-status',
  templateUrl: './status.component.html',
  styleUrls: ['./status.component.css']
})
export class StatusComponent implements OnInit {
  status: Observable<BenchmarksStatus>;

  constructor(private statusService: BenchmarkStatusService,
              private router: Router) {
    this.status = this.statusService.getStatus();
  }

  ngOnInit() {
  }

  getEntries = _getEntries
}
