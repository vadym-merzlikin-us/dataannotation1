import { Component, input, output } from '@angular/core';

@Component({
  selector: 'app-collapsible',
  templateUrl: './collapsible.html',
  styleUrl: './collapsible.scss',
})
export class Collapsible {
  readonly title = input.required<string>();
  readonly expanded = input(false);

  readonly expandedChange = output<boolean>();

  protected toggle(): void {
    this.expandedChange.emit(!this.expanded());
  }
}
