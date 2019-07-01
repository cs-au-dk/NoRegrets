export class MissingImplementationException implements Error {
  readonly columnNumber: number;
  readonly fileName: string;
  readonly lineNumber: number;
  message: string;

  constructor(public name: string) {}

  dumpStack() {}

  getStackTrace(): any[] { return undefined; }

  printStackTrace() {}
}

export class StuckTestException implements Error {
  name: string;
  stack: string;

  constructor(public message: string) {}
}

export class UnSynthesizableException implements Error {
  name: string;
  stack: string;

  constructor(public message: string) {}
}