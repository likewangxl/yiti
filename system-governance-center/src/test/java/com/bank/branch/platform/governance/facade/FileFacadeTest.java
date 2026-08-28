package com.bank.branch.platform.governance.facade;

import com.bank.branch.platform.governance.service.FileService;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class FileFacadeTest {

    @Test
    void writeFileContent_delegatesToFileService() {
        FileService fileService = mock(FileService.class);
        FileFacade facade = new FileFacade(fileService);
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        facade.writeFileContent("F_001", output);

        verify(fileService).writeFileContent("F_001", output);
    }

    @Test
    void unbindFile_delegatesToFileService() {
        FileService fileService = mock(FileService.class);
        FileFacade facade = new FileFacade(fileService);

        facade.unbindFile("ASSET_PROJECT", "9001", "F_001");

        verify(fileService).unbindFile("ASSET_PROJECT", "9001", "F_001");
    }
}
