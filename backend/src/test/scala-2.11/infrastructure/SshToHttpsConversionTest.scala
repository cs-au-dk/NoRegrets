package infrastructure

import backend.utils.Utils.sshtohttps
import org.specs2.mutable._
class SshToHttpsConversionTest extends Specification {

  "SshToHttpsConversion" >> {

    "On git+ssh" >> {
      sshtohttps("git+ssh://git@github.com:algobardo/project.git") mustEqual "https://github.com/algobardo/project.git"
    }
    "On git+https1" >> {
      sshtohttps("git+https://github.com/algobardo/project.git") mustEqual "https://github.com/algobardo/project.git"
    }
    "On git+https2" >> {
      sshtohttps("git+https://github.com:algobardo/project.git") mustEqual "https://github.com/algobardo/project.git"
    }
    "On git+https2 with git@" >> {
      sshtohttps("git+ssh://git@github.com/treelinehq/machinepack-machines.git") mustEqual "https://github.com/treelinehq/machinepack-machines.git"
    }
    "On git" >> {
      sshtohttps("git://github.com/bahmutov/boggle.git") mustEqual "https://github.com/bahmutov/boggle.git"
    }
    "On https" >> {
      sshtohttps("https://github.com/bahmutov/boggle.git") mustEqual "https://github.com/bahmutov/boggle.git"
    }
    "No protocol" >> {
      sshtohttps("git@github.com:adrai/node-cqrs-eventdenormalizer.git") mustEqual "https://github.com/adrai/node-cqrs-eventdenormalizer.git"
    }
  }

}
