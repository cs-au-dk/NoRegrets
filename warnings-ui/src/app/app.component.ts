import { Component }          from '@angular/core';

@Component({
  selector: 'my-app',
  template: `
      <h1>{{title}}</h1>
      <nav class="navbar navbar-default">
          <div class="container-fluid">
              <ul class="nav navbar-nav">
                  <li><a routerLink="/status" routerLinkActive="active">Type regressions</a> </li>
                  <li><a routerLink="/client-details" routerLinkActive="active">Client details</a></li>
              </ul>
          </div>
      </nav>
      <router-outlet></router-outlet>
  `,
  styleUrls: ['./app.component.css']
})
export class AppComponent {
  title = 'NoRegrets: Type Regression Visualization Utility';
}
