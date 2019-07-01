/**
 * Recursive data structure representing mocha tests
 * Note. Mocha has a more fine grained representation distinguishing test suites
 * from single test. However, this abstraction should be alright for our needs.
 */
export interface MochaTest {
  title: string;
  parent?: MochaTest;
  root?: boolean;
}

/**
 * @param {MochaTest} t
 * @returns the full name in a colon seperated form
 */
export function getFullTestName(t: MochaTest): string {
  return t.title + ':' + t.root ? "" : getFullTestName(t.parent);
}
