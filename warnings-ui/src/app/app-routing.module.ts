import { NgModule }             from '@angular/core';
import { RouterModule, Routes } from '@angular/router';

import {LogComponent}           from "./log/log.component";
import {ClientDetailsComponent}        from "./client-details/client-details.component";
import {ObservationComponent}   from "./observations/observation.component";
import {DiffComponent} from "./diffs/diffs.component";
import {StatusComponent} from "./status/status.component";

const routes: Routes = [
  { path: '', redirectTo: '/status', pathMatch: 'full' },
  { path: 'log/:key',     component: LogComponent },
  { path: 'observations/:key',     component: ObservationComponent },
  { path: 'client-details',     component: ClientDetailsComponent },
  { path: 'status',     component: StatusComponent },
  { path: 'diffs/:key/:version/:path', component: DiffComponent }
];

@NgModule({
  imports: [ RouterModule.forRoot(routes, { useHash: true }) ],
  exports: [ RouterModule ]
})
export class AppRoutingModule {}
