package com.riwi.skillbridge.infrastructure.adapter.in.rest.validation;
import com.riwi.skillbridge.domain.exception.InvalidMediaFileException;
import com.riwi.skillbridge.infrastructure.config.MediaUploadProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import static org.junit.jupiter.api.Assertions.*;
class MediaFileValidatorTest {
 private final MediaUploadProperties properties=new MediaUploadProperties(); private final MediaFileValidator validator=new MediaFileValidator(properties);
 @Test void acceptsAudio(){ assertDoesNotThrow(()->validator.audio(new MockMultipartFile("file","a.mp3","audio/mpeg",new byte[]{1}))); }
 @Test void rejectsEmptyImage(){ assertThrows(InvalidMediaFileException.class,()->validator.image(new MockMultipartFile("file","a.png","image/png",new byte[0]))); }
 @Test void rejectsInvalidAudioExtension(){ assertThrows(InvalidMediaFileException.class,()->validator.audio(new MockMultipartFile("file","a.exe","audio/mpeg",new byte[]{1}))); }
 @Test void rejectsOversizedImage(){ properties.setMaxImageBytes(1); assertThrows(InvalidMediaFileException.class,()->validator.image(new MockMultipartFile("file","a.png","image/png",new byte[]{1,2}))); }
 @Test void rejectsInvalidAudioType(){ assertThrows(InvalidMediaFileException.class,()->validator.audio(new MockMultipartFile("file","a.txt","text/plain",new byte[]{1}))); }
}
