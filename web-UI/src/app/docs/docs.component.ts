import {Component, OnInit} from '@angular/core';
import {MarkdownService} from 'ngx-markdown';

@Component({
  selector : 'app-docs',
  templateUrl : './docs.component.html',
  styleUrls : [ './docs.component.css' ]
})
export class DocsComponent implements OnInit {
  constructor(private markdownService: MarkdownService) {}

  ngOnInit() {
    this.markdownService.renderer.image = function(href: string, title: string,
                                                   text: string): string {
      if (href === null) {
        return text;
      }

      var out = '<img style="max-width: 100%" class="img-responsive" src="' +
                href + '" alt="' + text + '"';
      if (title) {
        out += ' title="' + title + '"';
      }
      out += this.options.xhtml ? '/>' : '>';
      return out;
    }
  }
}
